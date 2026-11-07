use futures_util::StreamExt;
use serde::{Deserialize, Serialize};
use std::{
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use tokio::sync::Notify;
use uuid::Uuid;

const DOWN: &str = "https://speed.cloudflare.com/__down";
const UP: &str = "https://speed.cloudflare.com/__up";
const MIN_VALID_LATENCY_SAMPLES: u32 = 5; // Android MeasurementStatus.MINIMO_AMOSTRAS_LATENCIA_VALIDAS
const MIN_TRANSFER_SAMPLES: u32 = 2;
const MIN_TRANSFER_BYTES: u64 = 5_000_000;
// The desktop has one mode: the complete 18 s download + 18 s upload window
// characterized by Android's SpeedtestConfig.complete.
const COMPLETE_TRANSFER_DURATION: Duration = Duration::from_secs(18);
const MAX_PHASE_BYTES: u64 = 2_500_000_000;

#[derive(Clone, Default)]
pub struct Cancellation {
    flag: Arc<AtomicBool>,
    notify: Arc<Notify>,
}
impl Cancellation {
    pub fn cancel(&self) {
        self.flag.store(true, Ordering::SeqCst);
        self.notify.notify_waiters();
    }
    fn cancelled(&self) -> bool {
        self.flag.load(Ordering::SeqCst)
    }
    async fn until_cancelled(&self) {
        let notified = self.notify.notified();
        tokio::pin!(notified);
        notified.as_mut().enable();
        if !self.cancelled() {
            notified.await;
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PhaseError {
    pub code: String,
    pub http_status: Option<u16>,
    pub message: String,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Phase {
    pub name: String,
    pub method: String,
    pub endpoint: String,
    pub started_at_ms: u64,
    pub duration_ms: u64,
    pub attempts: u32,
    pub valid_samples: u32,
    pub bytes_transferred: Option<u64>,
    pub status: String,
    pub error: Option<PhaseError>,
    pub fallback_used: bool,
    #[serde(default)]
    pub fallback_trigger: Option<PhaseError>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Measurement {
    pub schema_version: u8,
    pub execution_id: String,
    pub started_at_ms: u64,
    pub captured_at_ms: u64,
    pub status: String,
    pub download_mbps: Option<f64>,
    pub upload_mbps: Option<f64>,
    pub latency_ms: Option<f64>,
    pub jitter_ms: Option<f64>,
    pub packet_loss_percent: Option<f64>,
    pub packet_loss_source: String,
    pub bufferbloat_ms: Option<f64>,
    pub limitation: Option<String>,
    pub phases: Vec<Phase>,
    pub legacy_status: Option<String>,
    #[serde(default)]
    pub network_id: Option<String>,
    #[serde(default)]
    pub ssid: Option<String>,
    #[serde(default)]
    pub local_ip: Option<String>,
}
pub fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
fn median(samples: &[f64]) -> Option<f64> {
    if samples.is_empty() {
        return None;
    }
    let mut v = samples.to_vec();
    v.sort_by(f64::total_cmp);
    let m = v.len() / 2;
    Some(if v.len().is_multiple_of(2) {
        (v[m - 1] + v[m]) / 2.0
    } else {
        v[m]
    })
}
fn jitter(samples: &[f64]) -> Option<f64> {
    if samples.len() < 2 {
        return None;
    }
    Some(samples.windows(2).map(|v| (v[1] - v[0]).abs()).sum::<f64>() / (samples.len() - 1) as f64)
}
fn failure(code: &str, status: Option<u16>) -> PhaseError {
    PhaseError {
        code: code.into(),
        http_status: status,
        message: match status {
            Some(s) => format!("Destino de medição respondeu HTTP {s}."),
            None => match code {
                "TIMEOUT" => "Prazo da fase esgotado.",
                "CANCELLED" => "Medição cancelada.",
                "INSUFFICIENT_SAMPLE" => "Amostra insuficiente.",
                "DATA_LIMIT" => {
                    "Limite de dados da fase atingido antes de completar a janela de medição."
                }
                _ => "Não foi possível concluir a fase.",
            }
            .into(),
        },
    }
}
fn req_error(error: &reqwest::Error) -> PhaseError {
    failure(
        if error.is_timeout() {
            "TIMEOUT"
        } else {
            "TRANSPORT"
        },
        None,
    )
}
fn http_error(status: u16) -> PhaseError {
    failure(
        if status == 403 || status == 429 {
            "HTTP_BLOCKED"
        } else {
            "HTTP_ERROR"
        },
        Some(status),
    )
}
fn phase(name: &str, method: &str, endpoint: &str) -> Phase {
    Phase {
        name: name.into(),
        method: method.into(),
        endpoint: endpoint.into(),
        started_at_ms: now_ms(),
        duration_ms: 0,
        attempts: 0,
        valid_samples: 0,
        bytes_transferred: None,
        status: "INCONCLUSIVE".into(),
        error: None,
        fallback_used: false,
        fallback_trigger: None,
    }
}
fn close_phase(p: &mut Phase, clock: Instant) {
    p.duration_ms = clock.elapsed().as_millis() as u64;
    p.status = if p.error.is_some() {
        "FAILED"
    } else {
        "COMPLETE"
    }
    .into();
}
fn mbps(bytes: u64, elapsed: Duration) -> Option<f64> {
    if bytes == 0 || elapsed.as_millis() == 0 {
        None
    } else {
        Some(bytes as f64 * 8.0 / elapsed.as_secs_f64() / 1_000_000.0)
    }
}
fn transfer_sufficient(samples: u32, bytes: u64, elapsed: Duration) -> bool {
    samples >= MIN_TRANSFER_SAMPLES
        && bytes >= MIN_TRANSFER_BYTES
        && elapsed >= COMPLETE_TRANSFER_DURATION
}
fn classify(phases: &[Phase], cancelled: bool) -> &'static str {
    if cancelled {
        "CANCELLED"
    } else if phases.iter().any(|p| {
        p.error
            .as_ref()
            .is_some_and(|e| e.code == "INSUFFICIENT_SAMPLE" || e.code == "DATA_LIMIT")
    }) {
        "INCONCLUSIVE"
    } else if phases.iter().all(|p| p.status == "COMPLETE") {
        "COMPLETE"
    } else if phases.iter().any(|p| p.status == "COMPLETE") {
        "PARTIAL"
    } else {
        "INCONCLUSIVE"
    }
}
fn record_fallback(p: &mut Phase, trigger: PhaseError) {
    p.fallback_used = true;
    p.fallback_trigger = Some(trigger);
    p.error = None;
}
fn cancelled_error() -> PhaseError {
    failure("CANCELLED", None)
}
fn timed_out_phase(name: &str, method: &str, endpoint: &str, duration: Duration) -> Phase {
    let mut p = phase(name, method, endpoint);
    p.duration_ms = duration.as_millis() as u64;
    p.status = "FAILED".into();
    p.error = Some(failure("TIMEOUT", None));
    p
}

async fn latency(
    client: &reqwest::Client,
    cancel: &Cancellation,
) -> (Phase, Option<f64>, Option<f64>, Option<f64>) {
    let mut p = phase("latency", "HTTP GET", DOWN);
    let clock = Instant::now();
    let mut values = Vec::new();
    let mut timed_out = 0u32;
    let mut non_timeout_failure = false;
    for _ in 0..8 {
        if cancel.cancelled() {
            p.error = Some(cancelled_error());
            break;
        }
        p.attempts += 1;
        let start = Instant::now();
        let request = client
            .get(format!("{DOWN}?bytes=0&r={}", Uuid::new_v4()))
            .send();
        let response = tokio::select! { _=cancel.until_cancelled()=>{p.error=Some(cancelled_error());break}, r=request=>r};
        match response {
            Ok(r) if r.status().is_success() => {
                values.push(start.elapsed().as_secs_f64() * 1000.0);
                p.valid_samples += 1;
            }
            Ok(r) => {
                non_timeout_failure = true;
                p.error = Some(http_error(r.status().as_u16()))
            }
            Err(e) => {
                if e.is_timeout() {
                    timed_out += 1;
                } else {
                    non_timeout_failure = true;
                }
                p.error = Some(req_error(&e))
            }
        }
    }
    if p.valid_samples < MIN_VALID_LATENCY_SAMPLES && !cancel.cancelled() {
        p.error = Some(failure("INSUFFICIENT_SAMPLE", None));
    }
    if p.valid_samples >= MIN_VALID_LATENCY_SAMPLES {
        p.error = None;
    }
    close_phase(&mut p, clock);
    let valid = p.valid_samples >= MIN_VALID_LATENCY_SAMPLES;
    let loss = if valid && !non_timeout_failure {
        Some(timed_out as f64 / p.attempts as f64 * 100.0)
    } else {
        None
    };
    (
        p,
        if valid { median(&values) } else { None },
        if valid { jitter(&values) } else { None },
        loss,
    )
}

async fn download(client: &reqwest::Client, cancel: &Cancellation) -> (Phase, Option<f64>) {
    let mut p = phase("download", "HTTP GET", DOWN);
    let clock = Instant::now();
    let mut total = 0u64;
    // Android's blocked-request fallback uses a smaller payload.
    let mut request_size = 10_000_000u64;
    while clock.elapsed() < COMPLETE_TRANSFER_DURATION {
        if cancel.cancelled() {
            p.error = Some(cancelled_error());
            break;
        }
        let size = request_size;
        if total + size > MAX_PHASE_BYTES {
            p.error = Some(failure("DATA_LIMIT", None));
            break;
        }
        p.attempts += 1;
        let req = client
            .get(format!("{DOWN}?bytes={size}&r={}", Uuid::new_v4()))
            .header("Origin", "https://speed.cloudflare.com")
            .header("Referer", "https://speed.cloudflare.com/")
            .header("Accept", "*/*")
            .header("Cache-Control", "no-store")
            .send();
        let resp = tokio::select! {_=cancel.until_cancelled()=>{p.error=Some(cancelled_error());break},r=req=>r};
        let resp = match resp {
            Ok(r) if r.status().is_success() => r,
            Ok(r) => {
                p.error = Some(http_error(r.status().as_u16()));
                if (r.status().as_u16() == 403 || r.status().as_u16() == 429) && !p.fallback_used {
                    record_fallback(&mut p, http_error(r.status().as_u16()));
                    request_size = 5_000_000;
                    continue;
                }
                break;
            }
            Err(e) => {
                p.error = Some(req_error(&e));
                break;
            }
        };
        let mut stream = resp.bytes_stream();
        let mut count = 0u64;
        loop {
            let next = tokio::select! {_=cancel.until_cancelled()=>{p.error=Some(cancelled_error());break},r=stream.next()=>r};
            match next {
                Some(Ok(chunk)) => {
                    count += chunk.len() as u64;
                    if count > size || total + count > MAX_PHASE_BYTES {
                        p.error = Some(failure("INSUFFICIENT_SAMPLE", None));
                        break;
                    }
                }
                Some(Err(e)) => {
                    p.error = Some(req_error(&e));
                    break;
                }
                None => break,
            }
        }
        if p.error.is_some() {
            break;
        }
        if count < size {
            p.error = Some(failure("INSUFFICIENT_SAMPLE", None));
            break;
        }
        total += count;
        p.valid_samples += 1;
    }
    p.bytes_transferred = Some(total);
    if p.error.is_none() && !transfer_sufficient(p.valid_samples, total, clock.elapsed()) {
        p.error = Some(failure("INSUFFICIENT_SAMPLE", None));
    }
    let speed = if p.error.is_none() {
        mbps(total, clock.elapsed())
    } else {
        None
    };
    close_phase(&mut p, clock);
    (p, speed)
}

async fn upload(client: &reqwest::Client, cancel: &Cancellation) -> (Phase, Option<f64>) {
    let mut p = phase("upload", "HTTP POST", UP);
    let clock = Instant::now();
    let mut total = 0u64;
    while clock.elapsed() < COMPLETE_TRANSFER_DURATION {
        if cancel.cancelled() {
            p.error = Some(cancelled_error());
            break;
        }
        if total + 5_000_000 > MAX_PHASE_BYTES {
            p.error = Some(failure("DATA_LIMIT", None));
            break;
        }
        p.attempts += 1;
        let payload = vec![0x5a; 5_000_000];
        let req = client
            .post(UP)
            .header("Cache-Control", "no-store")
            .body(payload)
            .send();
        let result = tokio::select! {_=cancel.until_cancelled()=>{p.error=Some(cancelled_error());break},r=req=>r};
        match result {
            Ok(r) if r.status().is_success() => {
                total += 5_000_000;
                p.valid_samples += 1;
            }
            Ok(r) => {
                p.error = Some(http_error(r.status().as_u16()));
                break;
            }
            Err(e) => {
                p.error = Some(req_error(&e));
                break;
            }
        }
    }
    p.bytes_transferred = Some(total);
    if p.error.is_none() && !transfer_sufficient(p.valid_samples, total, clock.elapsed()) {
        p.error = Some(failure("INSUFFICIENT_SAMPLE", None));
    }
    let speed = if p.error.is_none() {
        mbps(total, clock.elapsed())
    } else {
        None
    };
    close_phase(&mut p, clock);
    (p, speed)
}

pub async fn measure<F: Fn(&str, u8)>(
    id: String,
    cancel: Cancellation,
    progress: F,
) -> Measurement {
    let started_at_ms = now_ms();
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(8))
        .user_agent("Mozilla/5.0 SignallQ-Desktop/0.1")
        .build()
        .expect("HTTP client configuration");
    let download_client = reqwest::Client::builder()
        .timeout(Duration::from_secs(8))
        .http1_only()
        .user_agent("Mozilla/5.0 SignallQ-Desktop/0.1")
        .build()
        .expect("HTTP download client configuration");
    progress("latency", 5);
    let (l, latency_ms, jitter_ms, packet_loss_percent) =
        tokio::time::timeout(Duration::from_secs(20), latency(&client, &cancel))
            .await
            .unwrap_or_else(|_| {
                (
                    timed_out_phase("latency", "HTTP GET", DOWN, Duration::from_secs(20)),
                    None,
                    None,
                    None,
                )
            });
    progress("download", 33);
    let (d, download_mbps) = if cancel.cancelled() {
        let mut p = phase("download", "HTTP GET", DOWN);
        p.error = Some(cancelled_error());
        (p, None)
    } else {
        tokio::time::timeout(Duration::from_secs(30), download(&download_client, &cancel))
            .await
            .unwrap_or_else(|_| {
                (
                    timed_out_phase("download", "HTTP GET", DOWN, Duration::from_secs(30)),
                    None,
                )
            })
    };
    progress("upload", 66);
    let (u, upload_mbps) = if cancel.cancelled() {
        let mut p = phase("upload", "HTTP POST", UP);
        p.error = Some(cancelled_error());
        (p, None)
    } else {
        tokio::time::timeout(Duration::from_secs(30), upload(&client, &cancel))
            .await
            .unwrap_or_else(|_| {
                (
                    timed_out_phase("upload", "HTTP POST", UP, Duration::from_secs(30)),
                    None,
                )
            })
    };
    let phases = vec![l, d, u];
    let status = classify(&phases, cancel.cancelled());
    progress("finished", 100);
    Measurement{schema_version:2,execution_id:id,started_at_ms,captured_at_ms:now_ms(),status:status.into(),download_mbps:if status=="CANCELLED"{None}else{download_mbps},upload_mbps:if status=="CANCELLED"{None}else{upload_mbps},latency_ms:if status=="CANCELLED"{None}else{latency_ms},jitter_ms:if status=="CANCELLED"{None}else{jitter_ms},packet_loss_percent:if status=="CANCELLED"{None}else{packet_loss_percent},packet_loss_source:if status=="CANCELLED"||packet_loss_percent.is_none(){"none"}else{"estimated_http_probe"}.into(),bufferbloat_ms:None,limitation:Some("Perda estimada por HTTP, não ICMP; atraso sob carga indisponível. Mudança de interface, IP ou SSID invalida os valores quando o Windows expõe esses dados.".into()),phases,legacy_status:None,network_id:None,ssid:None,local_ip:None}
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn zero_or_short_transfer_is_not_throughput() {
        assert!(!transfer_sufficient(1, 10_000_000, Duration::from_secs(1)));
        assert!(!transfer_sufficient(2, 1_000_000, Duration::from_secs(1)));
        assert!(!transfer_sufficient(
            2,
            10_000_000,
            Duration::from_millis(17_999)
        ));
        assert!(transfer_sufficient(2, 10_000_000, Duration::from_secs(18)));
        assert_eq!(mbps(2_000_000, Duration::ZERO), None);
    }
    #[test]
    fn http_blocked_is_distinct() {
        assert_eq!(http_error(403).code, "HTTP_BLOCKED");
        assert_eq!(http_error(429).code, "HTTP_BLOCKED");
        assert_eq!(http_error(500).code, "HTTP_ERROR");
    }
    #[test]
    fn latency_statistics_match_android_basics() {
        assert_eq!(median(&[2.0, 8.0, 4.0]), Some(4.0));
        assert_eq!(jitter(&[2.0, 4.0, 8.0]), Some(3.0));
        assert_eq!(MIN_VALID_LATENCY_SAMPLES, 5);
        let mut latency_phase = phase("latency", "HTTP GET", DOWN);
        latency_phase.valid_samples = 4;
        latency_phase.error = Some(failure("INSUFFICIENT_SAMPLE", None));
        let mut download_phase = phase("download", "HTTP GET", DOWN);
        download_phase.status = "COMPLETE".into();
        assert_eq!(
            classify(&[latency_phase, download_phase], false),
            "INCONCLUSIVE"
        );
    }
    #[test]
    fn successful_fallback_preserves_original_blocking_status() {
        let mut p = phase("download", "HTTP GET", DOWN);
        record_fallback(&mut p, http_error(403));
        p.valid_samples = 2;
        p.status = "COMPLETE".into();
        assert!(p.error.is_none());
        assert_eq!(p.fallback_trigger.as_ref().unwrap().http_status, Some(403));
        let json = serde_json::to_string(&p).unwrap();
        assert!(json.contains("\"fallbackTrigger\""));
        assert!(json.contains("\"httpStatus\":403"));
    }
}
