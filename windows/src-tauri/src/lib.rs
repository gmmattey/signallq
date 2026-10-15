use futures_util::StreamExt;
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};
use std::{
    fs,
    process::Command,
    sync::Mutex,
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use tauri::Manager;
use uuid::Uuid;

const SPEED_DOWNLOAD_URL: &str = "https://speed.cloudflare.com/__down";
const SPEED_UPLOAD_URL: &str = "https://speed.cloudflare.com/__up";

struct Database(Mutex<Connection>);

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Measurement {
    execution_id: String,
    captured_at_ms: u64,
    status: String,
    download_mbps: Option<f64>,
    upload_mbps: Option<f64>,
    latency_ms: Option<f64>,
    jitter_ms: Option<f64>,
    packet_loss_percent: Option<f64>,
    packet_loss_source: String,
    bufferbloat_ms: Option<f64>,
    limitation: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct NetworkSnapshot {
    interface_name: Option<String>,
    connection_type: String,
    local_ip: Option<String>,
    gateway: Option<String>,
    dns_servers: Vec<String>,
    ssid: Option<String>,
    signal_percent: Option<u8>,
    channel: Option<String>,
    capability_note: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ToolResult {
    target: String,
    kind: String,
    output: String,
    status: String,
    limitation: Option<String>,
}

impl Database {
    fn open(app: &tauri::AppHandle) -> Result<Self, String> {
        let path = app
            .path()
            .app_data_dir()
            .map_err(|error| error.to_string())?;
        fs::create_dir_all(&path).map_err(|error| error.to_string())?;
        let connection =
            Connection::open(path.join("signallq.sqlite3")).map_err(|error| error.to_string())?;
        connection
            .execute_batch(
                "PRAGMA foreign_keys = ON;
             CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY);
             CREATE TABLE IF NOT EXISTS measurements (
               execution_id TEXT PRIMARY KEY NOT NULL,
               captured_at_ms INTEGER NOT NULL,
               status TEXT NOT NULL,
               download_mbps REAL,
               upload_mbps REAL,
               latency_ms REAL,
               jitter_ms REAL,
               packet_loss_percent REAL,
               packet_loss_source TEXT NOT NULL,
               bufferbloat_ms REAL,
               limitation TEXT
             );
             INSERT OR IGNORE INTO schema_migrations(version) VALUES (1);",
            )
            .map_err(|error| error.to_string())?;
        Ok(Self(Mutex::new(connection)))
    }

    fn insert(&self, measurement: &Measurement) -> Result<(), String> {
        let connection = self
            .0
            .lock()
            .map_err(|_| "Banco local indisponível".to_string())?;
        connection.execute(
            "INSERT INTO measurements (execution_id, captured_at_ms, status, download_mbps, upload_mbps, latency_ms, jitter_ms, packet_loss_percent, packet_loss_source, bufferbloat_ms, limitation)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
            params![measurement.execution_id, measurement.captured_at_ms as i64, measurement.status,
                measurement.download_mbps, measurement.upload_mbps, measurement.latency_ms,
                measurement.jitter_ms, measurement.packet_loss_percent, measurement.packet_loss_source,
                measurement.bufferbloat_ms, measurement.limitation],
        ).map_err(|error| error.to_string())?;
        Ok(())
    }
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

fn median(values: &[f64]) -> Option<f64> {
    if values.is_empty() {
        return None;
    }
    let mut sorted = values.to_vec();
    sorted.sort_by(|left, right| left.total_cmp(right));
    let middle = sorted.len() / 2;
    Some(if sorted.len() % 2 == 0 {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
        sorted[middle]
    })
}

fn jitter(values: &[f64]) -> Option<f64> {
    if values.len() < 2 {
        return None;
    }
    Some(
        values
            .windows(2)
            .map(|pair| (pair[1] - pair[0]).abs())
            .sum::<f64>()
            / (values.len() - 1) as f64,
    )
}

async fn latency_probe(client: &reqwest::Client) -> Result<f64, String> {
    let started = Instant::now();
    client
        .get(format!("{SPEED_DOWNLOAD_URL}?bytes=0&r={}", Uuid::new_v4()))
        .send()
        .await
        .map_err(|error| error.to_string())?
        .error_for_status()
        .map_err(|error| error.to_string())?;
    Ok(started.elapsed().as_secs_f64() * 1000.0)
}

async fn download_probe(client: &reqwest::Client) -> Result<f64, String> {
    let started = Instant::now();
    let response = client
        .get(format!(
            "{SPEED_DOWNLOAD_URL}?bytes=15000000&r={}",
            Uuid::new_v4()
        ))
        .send()
        .await
        .map_err(|error| error.to_string())?
        .error_for_status()
        .map_err(|error| error.to_string())?;
    let mut bytes = 0usize;
    let mut stream = response.bytes_stream();
    while let Some(chunk) = stream.next().await {
        bytes += chunk.map_err(|error| error.to_string())?.len();
    }
    let seconds = started.elapsed().as_secs_f64().max(0.001);
    Ok((bytes as f64 * 8.0) / seconds / 1_000_000.0)
}

async fn upload_probe(client: &reqwest::Client) -> Result<f64, String> {
    let payload = vec![0x5a; 2_000_000];
    let started = Instant::now();
    client
        .post(SPEED_UPLOAD_URL)
        .body(payload.clone())
        .send()
        .await
        .map_err(|error| error.to_string())?
        .error_for_status()
        .map_err(|error| error.to_string())?;
    let seconds = started.elapsed().as_secs_f64().max(0.001);
    Ok((payload.len() as f64 * 8.0) / seconds / 1_000_000.0)
}

#[tauri::command]
async fn run_speedtest(database: tauri::State<'_, Database>) -> Result<Measurement, String> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(20))
        .build()
        .map_err(|error| error.to_string())?;
    let mut attempts = 0usize;
    let mut samples = Vec::new();
    for _ in 0..8 {
        attempts += 1;
        if let Ok(latency) = latency_probe(&client).await {
            samples.push(latency);
        }
    }
    let latency = median(&samples);
    if latency.is_none() {
        return Err(
            "Não houve resposta válida do destino de medição. Nenhum resultado foi salvo."
                .to_string(),
        );
    }
    let download = download_probe(&client).await.ok();
    let upload = upload_probe(&client).await.ok();
    let complete = download.is_some() && upload.is_some();
    let measurement = Measurement {
        execution_id: Uuid::new_v4().to_string(),
        captured_at_ms: now_ms(),
        status: if complete {
            "COMPLETED".to_string()
        } else {
            "PARTIAL".to_string()
        },
        download_mbps: download,
        upload_mbps: upload,
        latency_ms: latency,
        jitter_ms: jitter(&samples),
        packet_loss_percent: Some(((attempts - samples.len()) as f64 / attempts as f64) * 100.0),
        packet_loss_source: "estimated_http_probe".to_string(),
        bufferbloat_ms: None,
        limitation: Some(if complete {
            "Atraso sob carga indisponível: a coleta concorrente ainda não foi validada."
                .to_string()
        } else {
            "Download ou upload não concluiu; resultado parcial não recebe diagnóstico conclusivo. Atraso sob carga indisponível.".to_string()
        }),
    };
    database.insert(&measurement)?;
    Ok(measurement)
}

#[tauri::command]
fn list_history(database: tauri::State<'_, Database>) -> Result<Vec<Measurement>, String> {
    let connection = database
        .0
        .lock()
        .map_err(|_| "Banco local indisponível".to_string())?;
    let mut statement = connection.prepare(
        "SELECT execution_id, captured_at_ms, status, download_mbps, upload_mbps, latency_ms, jitter_ms, packet_loss_percent, packet_loss_source, bufferbloat_ms, limitation
         FROM measurements ORDER BY captured_at_ms DESC LIMIT 100"
    ).map_err(|error| error.to_string())?;
    let records = statement
        .query_map([], |row| {
            Ok(Measurement {
                execution_id: row.get(0)?,
                captured_at_ms: row.get::<_, i64>(1)? as u64,
                status: row.get(2)?,
                download_mbps: row.get(3)?,
                upload_mbps: row.get(4)?,
                latency_ms: row.get(5)?,
                jitter_ms: row.get(6)?,
                packet_loss_percent: row.get(7)?,
                packet_loss_source: row.get(8)?,
                bufferbloat_ms: row.get(9)?,
                limitation: row.get(10)?,
            })
        })
        .map_err(|error| error.to_string())?;
    records
        .collect::<Result<Vec<_>, _>>()
        .map_err(|error| error.to_string())
}

#[cfg(target_os = "windows")]
fn windows_network_snapshot() -> NetworkSnapshot {
    NetworkSnapshot {
        interface_name: None,
        connection_type: "Adaptador Windows ainda não validado".to_string(),
        local_ip: None,
        gateway: None,
        dns_servers: vec![],
        ssid: None,
        signal_percent: None,
        channel: None,
        capability_note: Some(
            "A coleta por texto do ipconfig/netsh foi removida para não misturar adaptadores. A versão distribuída só exibirá interface, IP, gateway, DNS e Wi-Fi após o adaptador Windows tipado ser validado.".to_string(),
        ),
    }
}

#[cfg(not(target_os = "windows"))]
fn windows_network_snapshot() -> NetworkSnapshot {
    NetworkSnapshot {
        interface_name: None, connection_type: "Indisponível neste sistema".to_string(), local_ip: None,
        gateway: None, dns_servers: vec![], ssid: None, signal_percent: None, channel: None,
        capability_note: Some("O adaptador Windows só é executado em Windows; este host não comprova a coleta nativa.".to_string()),
    }
}

#[tauri::command]
fn network_snapshot() -> NetworkSnapshot {
    windows_network_snapshot()
}

fn safe_target(target: &str) -> bool {
    !target.is_empty()
        && target.len() <= 253
        && target.chars().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '.' | '-' | ':')
        })
}

#[tauri::command]
async fn run_tool(kind: String, target: String) -> Result<ToolResult, String> {
    if !safe_target(&target) {
        return Err("Use um domínio ou IP válido, sem espaços ou comandos.".to_string());
    }
    if kind == "dns" {
        let lookup_target = target.clone();
        let result = tokio::time::timeout(
            Duration::from_secs(5),
            tokio::net::lookup_host((lookup_target.as_str(), 0)),
        )
        .await;
        return match result {
            Ok(Ok(addresses)) => Ok(ToolResult {
                target,
                kind,
                output: addresses
                    .map(|address| address.ip().to_string())
                    .collect::<Vec<_>>()
                    .join("\n"),
                status: "COMPLETED".to_string(),
                limitation: None,
            }),
            Ok(Err(error)) => Ok(ToolResult {
                target,
                kind,
                output: error.to_string(),
                status: "FAILED".to_string(),
                limitation: Some(
                    "A resolução falhou; isto não identifica a causa da falha.".to_string(),
                ),
            }),
            Err(_) => Ok(ToolResult {
                target,
                kind,
                output: "Tempo esgotado".to_string(),
                status: "TIMEOUT".to_string(),
                limitation: Some("Timeout não é resposta DNS nem sucesso.".to_string()),
            }),
        };
    }
    let command = match kind.as_str() {
        "ping" => {
            #[cfg(target_os = "windows")]
            {
                Command::new("ping")
                    .args(["-n", "4", "-w", "1500", &target])
                    .output()
            }
            #[cfg(not(target_os = "windows"))]
            {
                Command::new("ping")
                    .args(["-c", "4", "-W", "1500", &target])
                    .output()
            }
        }
        "route" => {
            #[cfg(target_os = "windows")]
            {
                Command::new("tracert")
                    .args(["-d", "-h", "8", "-w", "1500", &target])
                    .output()
            }
            #[cfg(not(target_os = "windows"))]
            {
                Command::new("traceroute")
                    .args(["-n", "-m", "8", "-w", "2", &target])
                    .output()
            }
        }
        _ => return Err("Ferramenta não suportada.".to_string()),
    }
    .map_err(|error| format!("Não foi possível executar a ferramenta: {error}"))?;
    let mut output = String::from_utf8_lossy(&command.stdout).to_string();
    output.push_str(&String::from_utf8_lossy(&command.stderr));
    output.truncate(12_000);
    Ok(ToolResult {
        target,
        kind,
        output,
        status: if command.status.success() {
            "COMPLETED".to_string()
        } else {
            "FAILED".to_string()
        },
        limitation: Some(
            "A saída é evidência de uma sondagem pontual; não conclui causa raiz isoladamente."
                .to_string(),
        ),
    })
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            app.manage(Database::open(app.handle()).expect("falha ao inicializar SQLite local"));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            run_speedtest,
            list_history,
            network_snapshot,
            run_tool
        ])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar SignallQ")
}

#[cfg(test)]
mod tests {
    use super::{median, safe_target};

    #[test]
    fn median_preserves_the_middle_of_valid_samples() {
        assert_eq!(median(&[2.0, 8.0, 4.0]), Some(4.0));
    }

    #[test]
    fn tool_target_rejects_shell_metacharacters() {
        assert!(safe_target("example.com"));
        assert!(!safe_target("example.com; whoami"));
        assert!(!safe_target("example.com/path"));
    }
}
