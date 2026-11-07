//! Regional reference route probe. Payload and echo validation match Android SondaGameLiftBeacon.
use serde::Serialize;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tokio::net::UdpSocket;
use uuid::Uuid;

const HOST: &str = "gamelift-ping.sa-east-1.api.aws";
const PORT: u16 = 7770;
const SAMPLES: usize = 8;
const TIMEOUT: Duration = Duration::from_secs(2);

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GamerResult {
    pub captured_at_ms: u64,
    pub status: String,
    pub destination: String,
    pub method: String,
    pub samples_ms: Vec<Option<f64>>,
    pub median_ms: Option<f64>,
    pub jitter_ms: Option<f64>,
    pub loss_percent: Option<f64>,
    pub max_ms: Option<f64>,
    pub p95_ms: Option<f64>,
    pub limitation: String,
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

fn payload(session: &[u8; 16], sequence: u32, nonce: &[u8; 8]) -> [u8; 33] {
    let mut bytes = [0u8; 33];
    bytes[..4].copy_from_slice(b"SQPB");
    bytes[4] = 1;
    bytes[5..21].copy_from_slice(session);
    bytes[21..25].copy_from_slice(&sequence.to_be_bytes());
    bytes[25..].copy_from_slice(nonce);
    bytes
}

fn summarize(samples: Vec<Option<f64>>, destination: String) -> GamerResult {
    // Android AnalisadorAmostragemPing: drop warm-up, filter >3x raw median,
    // then use consecutive valid observations for jitter. Keep raw samples intact.
    let analyzed = if samples.is_empty() {
        &[][..]
    } else {
        &samples[1..]
    };
    let valid: Vec<f64> = analyzed.iter().flatten().copied().collect();
    let raw_median = median(&valid);
    let filtered: Vec<f64> = match raw_median {
        Some(m) if m > 0.0 => valid.iter().copied().filter(|v| *v <= m * 3.0).collect(),
        _ => valid.clone(),
    };
    let used = if filtered.is_empty() {
        &valid
    } else {
        &filtered
    };
    let median_ms = median(used);
    let jitter_ms = if used.len() < 2 {
        None
    } else {
        Some(
            used.windows(2)
                .map(|pair| (pair[1] - pair[0]).abs())
                .sum::<f64>()
                / (used.len() - 1) as f64,
        )
    };
    let loss_percent = if analyzed.is_empty() {
        None
    } else {
        Some((analyzed.len() - valid.len()) as f64 / analyzed.len() as f64 * 100.0)
    };
    let status = if analyzed.is_empty() || valid.is_empty() {
        "INCONCLUSIVE"
    } else if valid.len() == analyzed.len() {
        "COMPLETE"
    } else {
        "PARTIAL"
    };
    let max_ms = valid.iter().copied().reduce(f64::max);
    let p95_ms = if valid.is_empty() {
        None
    } else {
        let mut sorted = valid.clone();
        sorted.sort_by(f64::total_cmp);
        Some(sorted[((0.95 * sorted.len() as f64).ceil() as usize).saturating_sub(1)])
    };
    GamerResult { captured_at_ms: now_ms(), status: status.into(), destination,
        method: "UDP echo SQPB v1".into(), samples_ms: samples, median_ms, jitter_ms, loss_percent, max_ms, p95_ms,
        limitation: "Rota regional pública de referência; não representa servidor de jogo ou partida. Ausência de eco UDP não prova perda na conexão.".into() }
}

fn median(values: &[f64]) -> Option<f64> {
    if values.is_empty() {
        return None;
    }
    let mut sorted = values.to_vec();
    sorted.sort_by(f64::total_cmp);
    let middle = sorted.len() / 2;
    Some(if sorted.len().is_multiple_of(2) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
        sorted[middle]
    })
}

async fn probe(host: &str, port: u16, count: usize, timeout: Duration) -> GamerResult {
    let destination = format!("{host}:{port}");
    let address = match tokio::time::timeout(
        Duration::from_secs(5),
        tokio::net::lookup_host((host, port)),
    )
    .await
    {
        Ok(Ok(mut addresses)) => match addresses.next() {
            Some(address) => address,
            None => return summarize(vec![], destination),
        },
        _ => return summarize(vec![], destination),
    };
    let bind = if address.is_ipv4() {
        "0.0.0.0:0"
    } else {
        "[::]:0"
    };
    let socket = match UdpSocket::bind(bind).await {
        Ok(socket) => socket,
        Err(_) => return summarize(vec![], destination),
    };
    let session = *Uuid::new_v4().as_bytes();
    let mut samples = Vec::with_capacity(count);
    for sequence in 0..count {
        let nonce_uuid = Uuid::new_v4();
        let mut nonce = [0u8; 8];
        nonce.copy_from_slice(&nonce_uuid.as_bytes()[..8]);
        let expected = payload(&session, sequence as u32, &nonce);
        let started = Instant::now();
        if socket.send_to(&expected, address).await.is_err() {
            samples.push(None);
            continue;
        }
        let mut response = [0u8; 128];
        let result = tokio::time::timeout(timeout, socket.recv_from(&mut response)).await;
        samples.push(match result {
            Ok(Ok((length, source))) if source == address && response[..length] == expected => {
                Some(started.elapsed().as_secs_f64() * 1000.0)
            }
            _ => None,
        });
    }
    summarize(samples, destination)
}

pub async fn probe_reference_route() -> GamerResult {
    probe(HOST, PORT, SAMPLES, TIMEOUT).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn payload_matches_android_format_and_network_byte_order() {
        let session = [0x11; 16];
        let bytes = payload(&session, 0x01020304, &[0x22; 8]);
        assert_eq!(bytes.len(), 33);
        assert_eq!(&bytes[..5], b"SQPB\x01");
        assert_eq!(&bytes[5..21], &session);
        assert_eq!(&bytes[21..25], &[1, 2, 3, 4]);
        assert_eq!(&bytes[25..], &[0x22; 8]);
    }

    #[test]
    fn timeout_is_missing_sample_and_partial_result() {
        let result = summarize(
            vec![Some(99.0), Some(10.0), None, Some(14.0)],
            "fixture:7770".into(),
        );
        assert_eq!(result.status, "PARTIAL");
        assert_eq!(result.median_ms, Some(12.0));
        assert_eq!(result.jitter_ms, Some(4.0));
        assert!((result.loss_percent.unwrap() - 100.0 / 3.0).abs() < 1e-9);
    }

    #[test]
    fn outlier_does_not_hide_peak_but_is_excluded_from_baseline() {
        let result = summarize(
            vec![Some(100.0), Some(10.0), Some(12.0), Some(100.0), Some(14.0)],
            "fixture:7770".into(),
        );
        assert_eq!(result.median_ms, Some(12.0));
        assert_eq!(result.jitter_ms, Some(2.0));
        assert_eq!(result.max_ms, Some(100.0));
        assert_eq!(result.p95_ms, Some(100.0));
    }

    #[tokio::test]
    async fn valid_local_echo_is_measured() {
        let echo = UdpSocket::bind("127.0.0.1:0").await.unwrap();
        let port = echo.local_addr().unwrap().port();
        tokio::spawn(async move {
            let mut buffer = [0u8; 128];
            for _ in 0..2 {
                let (len, peer) = echo.recv_from(&mut buffer).await.unwrap();
                echo.send_to(&buffer[..len], peer).await.unwrap();
            }
        });
        let result = probe("127.0.0.1", port, 2, Duration::from_millis(300)).await;
        assert_eq!(result.status, "COMPLETE");
        assert!(result.samples_ms.iter().all(Option::is_some));
    }
}
