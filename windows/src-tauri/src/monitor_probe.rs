//! Lightweight availability observations, separate from throughput measurements.
use serde::{Deserialize, Serialize};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use uuid::Uuid;

const HTTP_URL: &str = "https://speed.cloudflare.com/__down?bytes=0";
const DNS_HOST: &str = "cloudflare.com";
const HTTP_TIMEOUT: Duration = Duration::from_secs(10);
const DNS_TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MonitorSample {
    pub latency_ms: Option<f64>,
    pub status: String,
    pub http_status: Option<u16>,
    pub error_code: Option<String>,
    pub bytes_received: Option<u64>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MonitorResult {
    pub id: String,
    pub captured_at_ms: u64,
    pub source: String,
    pub status: String,
    pub method: String,
    pub endpoint: String,
    pub samples: Vec<MonitorSample>,
    pub latency_ms: Option<f64>,
    pub dns_ms: Option<f64>,
    pub dns_status: String,
    pub download_mbps: Option<f64>,
    pub upload_mbps: Option<f64>,
    pub limitation: String,
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

async fn http_sample(client: &reqwest::Client) -> MonitorSample {
    let start = Instant::now();
    match tokio::time::timeout(HTTP_TIMEOUT, client.get(HTTP_URL).send()).await {
        Err(_) => MonitorSample {
            latency_ms: None,
            status: "TIMEOUT".into(),
            http_status: None,
            error_code: Some("TIMEOUT".into()),
            bytes_received: None,
        },
        Ok(Err(error)) => MonitorSample {
            latency_ms: None,
            status: "FAILED".into(),
            http_status: None,
            error_code: Some(
                if error.is_timeout() {
                    "TIMEOUT"
                } else {
                    "TRANSPORT"
                }
                .into(),
            ),
            bytes_received: None,
        },
        Ok(Ok(response)) => {
            let code = response.status().as_u16();
            if !response.status().is_success() {
                return MonitorSample {
                    latency_ms: None,
                    status: "FAILED".into(),
                    http_status: Some(code),
                    error_code: Some(
                        if code == 403 || code == 429 {
                            "HTTP_BLOCKED"
                        } else {
                            "HTTP_ERROR"
                        }
                        .into(),
                    ),
                    bytes_received: None,
                };
            }
            // The Android worker times to response headers; no throughput is inferred.
            MonitorSample {
                latency_ms: Some(start.elapsed().as_secs_f64() * 1000.0),
                status: "COMPLETE".into(),
                http_status: Some(code),
                error_code: None,
                bytes_received: None,
            }
        }
    }
}

async fn dns_sample() -> (Option<f64>, String) {
    let start = Instant::now();
    match tokio::time::timeout(DNS_TIMEOUT, tokio::net::lookup_host((DNS_HOST, 0))).await {
        Ok(Ok(mut addresses)) => {
            if addresses.next().is_some() {
                (
                    Some(start.elapsed().as_secs_f64() * 1000.0),
                    "COMPLETE".into(),
                )
            } else {
                (None, "EMPTY".into())
            }
        }
        Ok(Err(_)) => (None, "FAILED".into()),
        Err(_) => (None, "TIMEOUT".into()),
    }
}

fn summarize(
    samples: Vec<MonitorSample>,
    dns_ms: Option<f64>,
    dns_status: String,
) -> MonitorResult {
    let mut valid: Vec<f64> = samples
        .iter()
        .filter_map(|sample| sample.latency_ms)
        .collect();
    valid.sort_by(f64::total_cmp);
    let latency_ms = if valid.is_empty() {
        None
    } else {
        Some(valid[valid.len() / 2])
    };
    let status = if latency_ms.is_some() && dns_ms.is_some() {
        "COMPLETE"
    } else if latency_ms.is_some() || dns_ms.is_some() {
        "PARTIAL"
    } else {
        "INCONCLUSIVE"
    };
    MonitorResult {
        id: Uuid::new_v4().to_string(),
        captured_at_ms: now_ms(),
        source: "monitor".into(),
        status: status.into(),
        method: "3 HTTP GET header timings + DNS lookup".into(),
        endpoint: "https://speed.cloudflare.com/__down".into(),
        samples,
        latency_ms,
        dns_ms,
        dns_status,
        download_mbps: None,
        upload_mbps: None,
        limitation:
            "Sondas leves pontuais; sem teste de velocidade, perda ICMP ou diagnóstico causal."
                .into(),
    }
}

pub async fn probe() -> MonitorResult {
    let client = match reqwest::Client::builder().timeout(HTTP_TIMEOUT).build() {
        Ok(client) => client,
        Err(_) => return summarize(vec![], None, "UNAVAILABLE".into()),
    };
    let (a, b, c, dns) = tokio::join!(
        http_sample(&client),
        http_sample(&client),
        http_sample(&client),
        dns_sample()
    );
    summarize(vec![a, b, c], dns.0, dns.1)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn failures_are_null_and_never_become_zero_or_speed() {
        let failure = MonitorSample {
            latency_ms: None,
            status: "TIMEOUT".into(),
            http_status: None,
            error_code: Some("TIMEOUT".into()),
            bytes_received: None,
        };
        let result = summarize(vec![failure; 3], None, "TIMEOUT".into());
        assert_eq!(result.status, "INCONCLUSIVE");
        assert!(result.latency_ms.is_none());
        assert!(result.download_mbps.is_none());
        assert!(result.upload_mbps.is_none());
    }

    #[test]
    fn three_samples_use_android_upper_middle_median() {
        let samples = [1.0, 5.0, 2.0]
            .map(|latency| MonitorSample {
                latency_ms: Some(latency),
                status: "COMPLETE".into(),
                http_status: Some(200),
                error_code: None,
                bytes_received: None,
            })
            .to_vec();
        let result = summarize(samples, None, "FAILED".into());
        assert_eq!(result.status, "PARTIAL");
        assert_eq!(result.latency_ms, Some(2.0));
    }
}
