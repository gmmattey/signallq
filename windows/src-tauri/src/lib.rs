mod gamer;
mod history_export;
mod monitor_probe;
mod network_windows;
mod speedtest;
use rusqlite::{params, Connection};
use serde::Serialize;
use speedtest::{Cancellation, Measurement};
use std::io::Write;
use std::{
    fs,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    time::{Duration, Instant},
};
use tauri::{
    menu::{Menu, MenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    Emitter, Manager,
};
use tauri_plugin_dialog::DialogExt;
use tokio::process::Command;
use uuid::Uuid;

#[derive(Clone)]
struct Database(Arc<Mutex<Connection>>);
#[derive(Default)]
struct Active(Mutex<Option<(String, Cancellation)>>);
#[derive(Clone, Default)]
struct ToolCancellation {
    cancelled: Arc<AtomicBool>,
    notify: Arc<tokio::sync::Notify>,
}
impl ToolCancellation {
    fn cancel(&self) {
        self.cancelled.store(true, Ordering::SeqCst);
        self.notify.notify_waiters();
    }
    async fn until_cancelled(&self) {
        let notified = self.notify.notified();
        tokio::pin!(notified);
        notified.as_mut().enable();
        if !self.cancelled.load(Ordering::SeqCst) {
            notified.await;
        }
    }
}
#[derive(Default)]
struct ActiveTool(Mutex<Option<ToolCancellation>>);
#[derive(Default)]
struct MonitorState(Mutex<Option<tauri::async_runtime::JoinHandle<()>>>);
#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Started {
    execution_id: String,
}
#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Progress {
    execution_id: String,
    phase: String,
    percent: u8,
}
#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Failed {
    execution_id: String,
    message: String,
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
    network_id: Option<String>,
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
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ToolResponse {
    #[serde(flatten)]
    result: ToolResult,
    started_at_ms: u64,
    duration_ms: u64,
}

static SHUTTING_DOWN: AtomicBool = AtomicBool::new(false);

fn shutdown(app: &tauri::AppHandle) {
    if SHUTTING_DOWN.swap(true, Ordering::SeqCst) {
        return;
    }
    if let Some(active) = app.try_state::<Active>() {
        if let Ok(guard) = active.0.lock() {
            if let Some((_, cancellation)) = guard.as_ref() {
                cancellation.cancel();
            }
        }
    }
    if let Some(monitor) = app.try_state::<MonitorState>() {
        if let Ok(mut guard) = monitor.0.lock() {
            if let Some(handle) = guard.take() {
                handle.abort();
            }
        }
    }
    if let Some(tool) = app.try_state::<ActiveTool>() {
        if let Ok(guard) = tool.0.lock() {
            if let Some(cancellation) = guard.as_ref() {
                cancellation.cancel();
            }
        }
    }
    app.exit(0);
}

impl Database {
    fn open(app: &tauri::AppHandle) -> Result<Self, String> {
        let path = app
            .path()
            .app_data_dir()
            .map_err(|error| error.to_string())?;
        fs::create_dir_all(&path).map_err(|error| error.to_string())?;
        let mut connection =
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
        migrate_v2(&mut connection)?;
        connection.execute_batch("CREATE TABLE IF NOT EXISTS monitor_samples(id TEXT PRIMARY KEY NOT NULL, captured_at_ms INTEGER NOT NULL, evidence_json TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS preferences(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS measurement_context(execution_id TEXT PRIMARY KEY NOT NULL REFERENCES measurements(execution_id) ON DELETE CASCADE, network_id TEXT, ssid TEXT, local_ip TEXT);").map_err(|error| error.to_string())?;
        connection.execute_batch("CREATE TABLE IF NOT EXISTS monitor_context(id TEXT PRIMARY KEY NOT NULL REFERENCES monitor_samples(id) ON DELETE CASCADE, network_id TEXT, ssid TEXT, local_ip TEXT);").map_err(|error| error.to_string())?;
        Ok(Self(Arc::new(Mutex::new(connection))))
    }

    fn insert(&self, measurement: &Measurement, network: &NetworkSnapshot) -> Result<(), String> {
        if measurement.schema_version != 2
            || !["COMPLETE", "PARTIAL", "INCONCLUSIVE"].contains(&measurement.status.as_str())
        {
            return Err("Estado de medição não persistível".into());
        }
        let evidence = serde_json::to_string(measurement).map_err(|error| error.to_string())?;
        let mut connection = self
            .0
            .lock()
            .map_err(|_| "Banco local indisponível".to_string())?;
        let transaction = connection
            .transaction()
            .map_err(|error| error.to_string())?;
        transaction.execute(
            "INSERT INTO measurements (execution_id, captured_at_ms, status, download_mbps, upload_mbps, latency_ms, jitter_ms, packet_loss_percent, packet_loss_source, bufferbloat_ms, limitation, schema_version, started_at_ms, evidence_json)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, 2, ?12, ?13)",
            params![measurement.execution_id, measurement.captured_at_ms as i64, if measurement.status=="COMPLETE"{"COMPLETED"}else{"PARTIAL"},
                measurement.download_mbps, measurement.upload_mbps, measurement.latency_ms,
                measurement.jitter_ms, measurement.packet_loss_percent, measurement.packet_loss_source,
                measurement.bufferbloat_ms, measurement.limitation, measurement.started_at_ms as i64, evidence],
        ).map_err(|error| error.to_string())?;
        transaction.execute("INSERT INTO measurement_context(execution_id,network_id,ssid,local_ip) VALUES(?1,?2,?3,?4)",
            params![measurement.execution_id, network.network_id, network.ssid, network.local_ip]).map_err(|error| error.to_string())?;
        transaction.commit().map_err(|error| error.to_string())?;
        Ok(())
    }

    fn monitor_enabled(&self) -> Result<bool, String> {
        let connection = self
            .0
            .lock()
            .map_err(|_| "Banco local indisponível".to_string())?;
        let mut statement = connection
            .prepare("SELECT value FROM preferences WHERE key='monitor_enabled'")
            .map_err(|e| e.to_string())?;
        let result: Result<String, _> = statement.query_row([], |row| row.get(0));
        match result {
            Ok(value) => Ok(value == "1"),
            Err(rusqlite::Error::QueryReturnedNoRows) => Ok(false),
            Err(error) => Err(error.to_string()),
        }
    }

    fn set_monitor_enabled(&self, enabled: bool) -> Result<(), String> {
        let connection = self
            .0
            .lock()
            .map_err(|_| "Banco local indisponível".to_string())?;
        connection.execute("INSERT INTO preferences(key,value) VALUES('monitor_enabled',?1) ON CONFLICT(key) DO UPDATE SET value=excluded.value", [if enabled { "1" } else { "0" }]).map_err(|e| e.to_string())?;
        Ok(())
    }

    fn insert_monitor(
        &self,
        result: &monitor_probe::MonitorResult,
        network: &NetworkSnapshot,
    ) -> Result<(), String> {
        let mut connection = self
            .0
            .lock()
            .map_err(|_| "Banco local indisponível".to_string())?;
        let json = serde_json::to_string(result).map_err(|e| e.to_string())?;
        let transaction = connection.transaction().map_err(|e| e.to_string())?;
        transaction
            .execute(
                "INSERT INTO monitor_samples(id,captured_at_ms,evidence_json) VALUES(?1,?2,?3)",
                params![result.id, result.captured_at_ms as i64, json],
            )
            .map_err(|e| e.to_string())?;
        transaction
            .execute(
                "INSERT INTO monitor_context(id,network_id,ssid,local_ip) VALUES(?1,?2,?3,?4)",
                params![
                    result.id,
                    network.network_id,
                    network.ssid,
                    network.local_ip
                ],
            )
            .map_err(|e| e.to_string())?;
        transaction.commit().map_err(|e| e.to_string())?;
        Ok(())
    }

    fn list_monitor(&self) -> Result<Vec<monitor_probe::MonitorResult>, String> {
        let connection = self
            .0
            .lock()
            .map_err(|_| "Banco local indisponível".to_string())?;
        let mut statement = connection
            .prepare(
                "SELECT evidence_json FROM monitor_samples ORDER BY captured_at_ms DESC LIMIT 500",
            )
            .map_err(|e| e.to_string())?;
        let values = statement
            .query_map([], |row| row.get::<_, String>(0))
            .map_err(|e| e.to_string())?;
        values
            .map(|value| {
                serde_json::from_str(&value.map_err(|e| e.to_string())?).map_err(|e| e.to_string())
            })
            .collect()
    }

    fn export_csv(&self, include_network_fields: bool) -> Result<(String, usize), String> {
        use history_export::{line, number, text, HEADER};
        let connection = self
            .0
            .lock()
            .map_err(|_| "Banco local indisponível".to_string())?;
        let mut csv = String::from("\u{feff}");
        csv.push_str(HEADER);
        let mut count = 0usize;
        let mut statement = connection.prepare("SELECT m.execution_id,m.captured_at_ms,m.status,m.download_mbps,m.upload_mbps,m.latency_ms,m.jitter_ms,m.packet_loss_percent,m.packet_loss_source,m.bufferbloat_ms,m.limitation,m.started_at_ms,m.evidence_json,c.network_id,c.ssid,c.local_ip FROM measurements m LEFT JOIN measurement_context c ON c.execution_id=m.execution_id ORDER BY m.captured_at_ms DESC").map_err(|e|e.to_string())?;
        let records = statement
            .query_map([], |row| {
                let evidence: Option<String> = row.get(12)?;
                let (status, start, phase_details, phase_errors) = if let Some(json) = evidence {
                    let record: Measurement = serde_json::from_str(&json).map_err(|e| {
                        rusqlite::Error::FromSqlConversionFailure(
                            12,
                            rusqlite::types::Type::Text,
                            Box::new(e),
                        )
                    })?;
                    let errors = record
                        .phases
                        .iter()
                        .filter_map(|phase| {
                            phase.error.as_ref().map(|error| {
                                format!(
                                    "{}:{}{}",
                                    phase.name,
                                    error.code,
                                    error
                                        .http_status
                                        .map(|s| format!(" HTTP {s}"))
                                        .unwrap_or_default()
                                )
                            })
                        })
                        .collect::<Vec<_>>()
                        .join(" | ");
                    let details = serde_json::to_string(&record.phases)
                        .map_err(|e| rusqlite::Error::ToSqlConversionFailure(Box::new(e)))?;
                    (
                        record.status,
                        record.started_at_ms.to_string(),
                        details,
                        errors,
                    )
                } else {
                    let legacy: String = row.get(2)?;
                    (
                        if legacy == "COMPLETED" {
                            "INCONCLUSIVE".into()
                        } else {
                            "PARTIAL".into()
                        },
                        row.get::<_, Option<i64>>(11)?
                            .unwrap_or(row.get(1)?)
                            .to_string(),
                        String::new(),
                        format!("legacy:{legacy}"),
                    )
                };
                Ok(vec![
                    "2".into(),
                    "speedtest".into(),
                    row.get::<_, String>(0)?,
                    start,
                    row.get::<_, i64>(1)?.to_string(),
                    status,
                    number(row.get(3)?),
                    number(row.get(4)?),
                    number(row.get(5)?),
                    number(row.get(6)?),
                    number(row.get(7)?),
                    row.get::<_, Option<String>>(8)?.unwrap_or_default(),
                    number(row.get(9)?),
                    String::new(),
                    if include_network_fields {
                        text(row.get::<_, Option<String>>(13)?.as_deref())
                    } else {
                        String::new()
                    },
                    if include_network_fields {
                        text(row.get::<_, Option<String>>(14)?.as_deref())
                    } else {
                        String::new()
                    },
                    if include_network_fields {
                        text(row.get::<_, Option<String>>(15)?.as_deref())
                    } else {
                        String::new()
                    },
                    text(row.get::<_, Option<String>>(10)?.as_deref()),
                    phase_details,
                    phase_errors,
                ])
            })
            .map_err(|e| e.to_string())?;
        for record in records {
            csv.push_str(&line(&record.map_err(|e| e.to_string())?));
            count += 1;
        }
        let mut statement = connection.prepare("SELECT m.evidence_json,c.network_id,c.ssid,c.local_ip FROM monitor_samples m LEFT JOIN monitor_context c ON c.id=m.id ORDER BY m.captured_at_ms DESC").map_err(|e|e.to_string())?;
        let records = statement
            .query_map([], |row| {
                let json: String = row.get(0)?;
                let record: monitor_probe::MonitorResult =
                    serde_json::from_str(&json).map_err(|e| {
                        rusqlite::Error::FromSqlConversionFailure(
                            0,
                            rusqlite::types::Type::Text,
                            Box::new(e),
                        )
                    })?;
                let errors = record
                    .samples
                    .iter()
                    .filter_map(|sample| sample.error_code.as_deref())
                    .collect::<Vec<_>>()
                    .join(" | ");
                let details = serde_json::to_string(&record.samples)
                    .map_err(|e| rusqlite::Error::ToSqlConversionFailure(Box::new(e)))?;
                Ok(vec![
                    "2".into(),
                    "monitor".into(),
                    record.id,
                    String::new(),
                    record.captured_at_ms.to_string(),
                    record.status,
                    String::new(),
                    String::new(),
                    number(record.latency_ms),
                    String::new(),
                    String::new(),
                    String::new(),
                    String::new(),
                    number(record.dns_ms),
                    if include_network_fields {
                        text(row.get::<_, Option<String>>(1)?.as_deref())
                    } else {
                        String::new()
                    },
                    if include_network_fields {
                        text(row.get::<_, Option<String>>(2)?.as_deref())
                    } else {
                        String::new()
                    },
                    if include_network_fields {
                        text(row.get::<_, Option<String>>(3)?.as_deref())
                    } else {
                        String::new()
                    },
                    record.limitation,
                    details,
                    errors,
                ])
            })
            .map_err(|e| e.to_string())?;
        for record in records {
            csv.push_str(&line(&record.map_err(|e| e.to_string())?));
            count += 1;
        }
        Ok((csv, count))
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ExportResult {
    saved: bool,
    rows: usize,
    path: Option<String>,
}

#[tauri::command]
fn export_history_csv(
    app: tauri::AppHandle,
    database: tauri::State<'_, Database>,
    include_network_fields: bool,
) -> Result<ExportResult, String> {
    let (csv, rows) = database.export_csv(include_network_fields)?;
    let selected = app
        .dialog()
        .file()
        .add_filter("CSV", &["csv"])
        .set_file_name("SignallQ-historico.csv")
        .blocking_save_file();
    let Some(selected) = selected else {
        return Ok(ExportResult {
            saved: false,
            rows: 0,
            path: None,
        });
    };
    let path = selected.into_path().map_err(|e| e.to_string())?;
    let temp = path.with_extension(format!("csv.tmp-{}", Uuid::new_v4()));
    let write = (|| -> Result<(), String> {
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&temp)
            .map_err(|e| e.to_string())?;
        file.write_all(csv.as_bytes()).map_err(|e| e.to_string())?;
        file.sync_all().map_err(|e| e.to_string())?;
        fs::rename(&temp, &path).map_err(|e| e.to_string())?;
        Ok(())
    })();
    if write.is_err() {
        let _ = fs::remove_file(&temp);
    }
    write?;
    Ok(ExportResult {
        saved: true,
        rows,
        path: Some(path.to_string_lossy().into_owned()),
    })
}

#[tauri::command]
fn delete_history_record(
    database: tauri::State<'_, Database>,
    record_type: String,
    id: String,
) -> Result<bool, String> {
    let table = match record_type.as_str() {
        "speedtest" => "measurements",
        "monitor" => "monitor_samples",
        _ => return Err("Tipo de registro inválido".into()),
    };
    let connection = database
        .0
        .lock()
        .map_err(|_| "Banco local indisponível".to_string())?;
    let changed = connection
        .execute(
            &format!(
                "DELETE FROM {table} WHERE {}=?1",
                if record_type == "speedtest" {
                    "execution_id"
                } else {
                    "id"
                }
            ),
            [id],
        )
        .map_err(|e| e.to_string())?;
    Ok(changed == 1)
}

#[tauri::command]
fn delete_all_history(database: tauri::State<'_, Database>) -> Result<(), String> {
    let mut connection = database
        .0
        .lock()
        .map_err(|_| "Banco local indisponível".to_string())?;
    let transaction = connection.transaction().map_err(|e| e.to_string())?;
    transaction
        .execute("DELETE FROM measurements", [])
        .map_err(|e| e.to_string())?;
    transaction
        .execute("DELETE FROM monitor_samples", [])
        .map_err(|e| e.to_string())?;
    transaction.commit().map_err(|e| e.to_string())
}

fn start_monitor_loop(app: &tauri::AppHandle) -> Result<(), String> {
    let state = app.state::<MonitorState>();
    let mut guard = state
        .0
        .lock()
        .map_err(|_| "Monitor indisponível".to_string())?;
    if guard.is_some() {
        return Ok(());
    }
    let app = app.clone();
    *guard = Some(tauri::async_runtime::spawn(async move {
        loop {
            let destination = speed_route().await;
            let mut network = route_snapshot(destination);
            let mut result = monitor_probe::probe().await;
            if network_changed(&network, &route_snapshot(destination)) {
                result.status = "CONTAMINATED".into();
                result.latency_ms = None;
                result.dns_ms = None;
                result.limitation =
                    "A rede mudou durante a sonda; observação sem vínculo confiável com SSID/IP."
                        .into();
                network.network_id = None;
                network.ssid = None;
                network.local_ip = None;
            }
            if let Some(database) = app.try_state::<Database>() {
                if let Err(error) = database.insert_monitor(&result, &network) {
                    let _ = app.emit("monitor:error", error);
                } else {
                    let _ = app.emit("monitor:sample", &result);
                }
            }
            tokio::time::sleep(Duration::from_secs(15 * 60)).await;
        }
    }));
    Ok(())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct MonitorStatus {
    enabled: bool,
    interval_minutes: u32,
    latest: Option<monitor_probe::MonitorResult>,
}

#[tauri::command]
fn monitor_status(database: tauri::State<'_, Database>) -> Result<MonitorStatus, String> {
    Ok(MonitorStatus {
        enabled: database.monitor_enabled()?,
        interval_minutes: 15,
        latest: database.list_monitor()?.into_iter().next(),
    })
}

#[tauri::command]
fn monitor_start(
    app: tauri::AppHandle,
    database: tauri::State<'_, Database>,
) -> Result<MonitorStatus, String> {
    database.set_monitor_enabled(true)?;
    start_monitor_loop(&app)?;
    monitor_status(database)
}

#[tauri::command]
fn monitor_stop(
    app: tauri::AppHandle,
    database: tauri::State<'_, Database>,
) -> Result<MonitorStatus, String> {
    database.set_monitor_enabled(false)?;
    let state = app.state::<MonitorState>();
    if let Some(handle) = state
        .0
        .lock()
        .map_err(|_| "Monitor indisponível".to_string())?
        .take()
    {
        handle.abort();
    }
    monitor_status(database)
}

#[tauri::command]
fn monitor_history(
    database: tauri::State<'_, Database>,
) -> Result<Vec<monitor_probe::MonitorResult>, String> {
    database.list_monitor()
}

fn migrate_v2(connection: &mut Connection) -> Result<(), String> {
    let transaction = connection.transaction().map_err(|e| e.to_string())?;
    let migrated: bool = transaction
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE version=2)",
            [],
            |r| r.get(0),
        )
        .map_err(|e| e.to_string())?;
    if !migrated {
        transaction
            .execute_batch(
                "ALTER TABLE measurements ADD COLUMN schema_version INTEGER;
            ALTER TABLE measurements ADD COLUMN started_at_ms INTEGER;
            ALTER TABLE measurements ADD COLUMN evidence_json TEXT;
            INSERT INTO schema_migrations(version) VALUES (2);",
            )
            .map_err(|e| e.to_string())?;
    }
    transaction.commit().map_err(|e| e.to_string())
}

fn network_changed(before: &NetworkSnapshot, after: &NetworkSnapshot) -> bool {
    before.network_id != after.network_id
        || before.local_ip != after.local_ip
        || before.ssid != after.ssid
}

fn mark_network_change(
    result: &mut Measurement,
    before: &NetworkSnapshot,
    after: &NetworkSnapshot,
) {
    if result.status != "CANCELLED" && network_changed(before, after) {
        result.status = "CONTAMINATED".into();
        result.download_mbps = None;
        result.upload_mbps = None;
        result.latency_ms = None;
        result.jitter_ms = None;
        result.packet_loss_percent = None;
        result.bufferbloat_ms = None;
        result.limitation = Some("A interface, o IP local ou o SSID mudou ou deixou de estar disponível durante a medição; os valores não são comparáveis.".into());
    }
}

async fn speed_route() -> Option<std::net::Ipv4Addr> {
    tokio::time::timeout(
        Duration::from_secs(5),
        tokio::net::lookup_host(("speed.cloudflare.com", 443)),
    )
    .await
    .ok()?
    .ok()?
    .find_map(|address| match address.ip() {
        std::net::IpAddr::V4(ip) => Some(ip),
        _ => None,
    })
}

fn route_snapshot(destination: Option<std::net::Ipv4Addr>) -> NetworkSnapshot {
    destination
        .map(network_windows::snapshot_for)
        .unwrap_or_else(|| {
            let mut snapshot = network_windows::snapshot();
            snapshot.network_id = None;
            snapshot.capability_note = Some(
                "Não foi possível resolver o destino da medição; vínculo da rede não confirmado."
                    .into(),
            );
            snapshot
        })
}

#[tauri::command]
async fn start_speedtest(
    execution_id: String,
    app: tauri::AppHandle,
    database: tauri::State<'_, Database>,
    active: tauri::State<'_, Active>,
) -> Result<Started, String> {
    let id = Uuid::parse_str(&execution_id)
        .map_err(|_| "Identificador da medição inválido".to_string())?
        .to_string();
    let cancellation = Cancellation::default();
    {
        let mut guard = active
            .0
            .lock()
            .map_err(|_| "Medição indisponível".to_string())?;
        if guard.is_some() {
            return Err("Já existe uma medição em andamento".into());
        }
        *guard = Some((id.clone(), cancellation.clone()));
    }
    let state = database.inner().clone();
    let destination = speed_route().await;
    let network = route_snapshot(destination);
    let execution = id.clone();
    tauri::async_runtime::spawn(async move {
        let progress_app = app.clone();
        let progress_id = execution.clone();
        let mut result =
            speedtest::measure(execution.clone(), cancellation, move |phase, percent| {
                let _ = progress_app.emit(
                    "speedtest:progress",
                    Progress {
                        execution_id: progress_id.clone(),
                        phase: phase.into(),
                        percent,
                    },
                );
            })
            .await;
        mark_network_change(&mut result, &network, &route_snapshot(destination));
        result.network_id = network.network_id.clone();
        result.ssid = network.ssid.clone();
        result.local_ip = network.local_ip.clone();
        let saved = if ["COMPLETE", "PARTIAL", "INCONCLUSIVE"].contains(&result.status.as_str()) {
            state.insert(&result, &network)
        } else {
            Ok(())
        };
        if let Err(message) = saved {
            let _ = app.emit(
                "speedtest:failed",
                Failed {
                    execution_id: execution.clone(),
                    message,
                },
            );
        } else {
            let _ = app.emit("speedtest:finished", &result);
        }
        if let Some(active) = app.try_state::<Active>() {
            if let Ok(mut guard) = active.0.lock() {
                if guard
                    .as_ref()
                    .is_some_and(|(current, _)| current == &execution)
                {
                    *guard = None;
                }
            }
        }
    });
    Ok(Started { execution_id: id })
}

#[tauri::command]
fn cancel_speedtest(
    execution_id: String,
    active: tauri::State<'_, Active>,
) -> Result<bool, String> {
    let guard = active
        .0
        .lock()
        .map_err(|_| "Medição indisponível".to_string())?;
    if let Some((id, cancellation)) = guard.as_ref() {
        if id == &execution_id {
            cancellation.cancel();
            return Ok(true);
        }
    }
    Ok(false)
}

#[tauri::command]
fn list_history(database: tauri::State<'_, Database>) -> Result<Vec<Measurement>, String> {
    let connection = database
        .0
        .lock()
        .map_err(|_| "Banco local indisponível".to_string())?;
    let mut statement = connection.prepare(
        "SELECT m.execution_id, m.captured_at_ms, m.status, m.download_mbps, m.upload_mbps, m.latency_ms, m.jitter_ms, m.packet_loss_percent, m.packet_loss_source, m.bufferbloat_ms, m.limitation, m.schema_version, m.started_at_ms, m.evidence_json, c.network_id, c.ssid, c.local_ip
         FROM measurements m LEFT JOIN measurement_context c ON c.execution_id=m.execution_id ORDER BY m.captured_at_ms DESC LIMIT 500"
    ).map_err(|error| error.to_string())?;
    let records = statement
        .query_map([], |row| {
            let evidence: Option<String> = row.get(13)?;
            if let Some(json) = evidence {
                let mut record: Measurement = serde_json::from_str(&json).map_err(|e| {
                    rusqlite::Error::FromSqlConversionFailure(
                        13,
                        rusqlite::types::Type::Text,
                        Box::new(e),
                    )
                })?;
                record.network_id = row.get(14)?;
                record.ssid = row.get(15)?;
                record.local_ip = row.get(16)?;
                if record.schema_version != 2 {
                    return Err(rusqlite::Error::InvalidQuery);
                }
                return Ok(record);
            }
            let legacy: String = row.get(2)?;
            Ok(Measurement {
                schema_version: 2,
                execution_id: row.get(0)?,
                started_at_ms: row.get::<_, Option<i64>>(12)?.unwrap_or(row.get(1)?) as u64,
                captured_at_ms: row.get::<_, i64>(1)? as u64,
                status: if legacy == "COMPLETED" {
                    "INCONCLUSIVE"
                } else {
                    "PARTIAL"
                }
                .into(),
                download_mbps: row.get(3)?,
                upload_mbps: row.get(4)?,
                latency_ms: row.get(5)?,
                jitter_ms: row.get(6)?,
                packet_loss_percent: row.get(7)?,
                packet_loss_source: row.get(8)?,
                bufferbloat_ms: row.get(9)?,
                limitation: row.get(10)?,
                phases: vec![],
                legacy_status: Some(legacy),
                network_id: row.get(14)?,
                ssid: row.get(15)?,
                local_ip: row.get(16)?,
            })
        })
        .map_err(|error| error.to_string())?;
    records
        .collect::<Result<Vec<_>, _>>()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn network_snapshot() -> NetworkSnapshot {
    network_windows::snapshot()
}

#[tauri::command]
async fn gamer_probe() -> gamer::GamerResult {
    gamer::probe_reference_route().await
}

fn safe_target(target: &str) -> bool {
    if target.is_empty() || target.len() > 253 {
        return false;
    }
    if target.parse::<std::net::IpAddr>().is_ok() {
        return true;
    }
    target.split('.').all(|label| {
        !label.is_empty()
            && label.len() <= 63
            && !label.starts_with('-')
            && !label.ends_with('-')
            && label.chars().all(|c| c.is_ascii_alphanumeric() || c == '-')
    })
}

#[tauri::command]
async fn run_tool(
    kind: String,
    target: String,
    active: tauri::State<'_, ActiveTool>,
) -> Result<ToolResponse, String> {
    if !safe_target(&target) {
        return Err("Use um domínio ou IP válido, sem espaços ou comandos.".into());
    }
    if !["ping", "dns", "route"].contains(&kind.as_str()) {
        return Err("Ferramenta não suportada.".into());
    }
    let cancellation = ToolCancellation::default();
    {
        let mut guard = active
            .0
            .lock()
            .map_err(|_| "Ferramenta indisponível".to_string())?;
        if guard.is_some() {
            return Err("Já existe uma ferramenta em execução".into());
        }
        *guard = Some(cancellation.clone());
    }
    let started_at_ms = speedtest::now_ms();
    let clock = Instant::now();
    let result = tokio::select! {
        value = run_tool_inner(kind.clone(), target.clone()) => value,
        _ = cancellation.until_cancelled() => Ok(ToolResult { target, kind, output: "Execução cancelada".into(), status: "CANCELLED".into(), limitation: Some("A ferramenta foi interrompida pelo usuário.".into()) }),
    };
    *active
        .0
        .lock()
        .map_err(|_| "Ferramenta indisponível".to_string())? = None;
    result.map(|result| ToolResponse {
        result,
        started_at_ms,
        duration_ms: clock.elapsed().as_millis() as u64,
    })
}

#[tauri::command]
fn cancel_tool(active: tauri::State<'_, ActiveTool>) -> Result<bool, String> {
    let guard = active
        .0
        .lock()
        .map_err(|_| "Ferramenta indisponível".to_string())?;
    if let Some(cancellation) = guard.as_ref() {
        cancellation.cancel();
        Ok(true)
    } else {
        Ok(false)
    }
}

async fn run_tool_inner(kind: String, target: String) -> Result<ToolResult, String> {
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
            Ok(Ok(addresses)) => {
                let values = addresses
                    .map(|address| address.ip().to_string())
                    .collect::<Vec<_>>();
                Ok(ToolResult {
                    target,
                    kind,
                    output: values.join("\n"),
                    status: if values.is_empty() {
                        "INCONCLUSIVE"
                    } else {
                        "COMPLETED"
                    }
                    .to_string(),
                    limitation: if values.is_empty() {
                        Some(
                            "O resolvedor não retornou endereços; isto não prova uma causa.".into(),
                        )
                    } else {
                        None
                    },
                })
            }
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
    let mut command = match kind.as_str() {
        "ping" => {
            #[cfg(target_os = "windows")]
            {
                let mut command = Command::new("ping");
                command.args(["-n", "4", "-w", "1500", &target]);
                command
            }
            #[cfg(not(target_os = "windows"))]
            {
                let mut command = Command::new("ping");
                command.args(["-c", "4", "-W", "2", &target]);
                command
            }
        }
        "route" => {
            #[cfg(target_os = "windows")]
            {
                let mut command = Command::new("tracert");
                command.args(["-d", "-h", "8", "-w", "1500", &target]);
                command
            }
            #[cfg(not(target_os = "windows"))]
            {
                let mut command = Command::new("traceroute");
                command.args(["-n", "-m", "8", "-w", "2", &target]);
                command
            }
        }
        _ => return Err("Ferramenta não suportada.".to_string()),
    };
    command.kill_on_drop(true);
    let command = match tokio::time::timeout(
        Duration::from_secs(if kind == "route" { 30 } else { 10 }),
        command.output(),
    )
    .await
    {
        Ok(Ok(result)) => result,
        Ok(Err(error)) => return Err(format!("Não foi possível executar a ferramenta: {error}")),
        Err(_) => {
            return Ok(ToolResult {
                target,
                kind,
                output: "Tempo esgotado".into(),
                status: "TIMEOUT".into(),
                limitation: Some("O processo foi encerrado após o limite de tempo.".into()),
            })
        }
    };
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
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            app.manage(Database::open(app.handle()).expect("falha ao inicializar SQLite local"));
            app.manage(Active::default());
            app.manage(ActiveTool::default());
            app.manage(MonitorState::default());
            let show = MenuItem::with_id(app, "show", "Abrir SignallQ", true, None::<&str>)?;
            let exit = MenuItem::with_id(app, "exit", "Sair", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &exit])?;
            let mut tray = TrayIconBuilder::new()
                .menu(&menu)
                .show_menu_on_left_click(false)
                .tooltip("SignallQ");
            if let Some(icon) = app.default_window_icon() {
                tray = tray.icon(icon.clone());
            }
            tray.on_menu_event(|app, event| match event.id().as_ref() {
                "show" => {
                    if let Some(window) = app.get_webview_window("main") {
                        let _ = window.show();
                        let _ = window.unminimize();
                        let _ = window.set_focus();
                    }
                }
                "exit" => shutdown(app),
                _ => {}
            })
            .on_tray_icon_event(|tray, event| {
                if let TrayIconEvent::Click {
                    button: MouseButton::Left,
                    button_state: MouseButtonState::Up,
                    ..
                } = event
                {
                    if let Some(window) = tray.app_handle().get_webview_window("main") {
                        let _ = window.show();
                        let _ = window.unminimize();
                        let _ = window.set_focus();
                    }
                }
            })
            .build(app)?;
            if app.state::<Database>().monitor_enabled().unwrap_or(false) {
                start_monitor_loop(app.handle()).map_err(std::io::Error::other)?;
            }
            Ok(())
        })
        .on_window_event(|window, event| match event {
            tauri::WindowEvent::Resized(_) => {
                if window.is_minimized().unwrap_or(false) {
                    let _ = window.hide();
                }
            }
            tauri::WindowEvent::CloseRequested { .. } | tauri::WindowEvent::Destroyed => {
                shutdown(window.app_handle())
            }
            _ => {}
        })
        .invoke_handler(tauri::generate_handler![
            start_speedtest,
            cancel_speedtest,
            list_history,
            network_snapshot,
            gamer_probe,
            monitor_status,
            monitor_start,
            monitor_stop,
            monitor_history,
            export_history_csv,
            delete_history_record,
            delete_all_history,
            run_tool,
            cancel_tool
        ])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar SignallQ")
}

#[cfg(test)]
mod tests {
    use super::{migrate_v2, network_changed, safe_target, NetworkSnapshot};
    use rusqlite::Connection;

    #[test]
    fn v1_rows_survive_idempotent_v2_migration_without_null_coercion() {
        let mut db = Connection::open_in_memory().unwrap();
        db.execute_batch("CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY);
          INSERT INTO schema_migrations VALUES(1);
          CREATE TABLE measurements(execution_id TEXT PRIMARY KEY NOT NULL,captured_at_ms INTEGER NOT NULL,status TEXT NOT NULL,
            download_mbps REAL,upload_mbps REAL,latency_ms REAL,jitter_ms REAL,packet_loss_percent REAL,
            packet_loss_source TEXT NOT NULL,bufferbloat_ms REAL,limitation TEXT);
          INSERT INTO measurements(execution_id,captured_at_ms,status,packet_loss_source) VALUES
            ('1',1,'PARTIAL','estimated_http_probe'),('2',2,'PARTIAL','estimated_http_probe'),
            ('3',3,'PARTIAL','estimated_http_probe'),('4',4,'PARTIAL','estimated_http_probe'),
            ('5',5,'PARTIAL','estimated_http_probe'),('6',6,'PARTIAL','estimated_http_probe');").unwrap();
        migrate_v2(&mut db).unwrap();
        migrate_v2(&mut db).unwrap();
        let (count, nonnull): (i64, i64) = db
            .query_row(
                "SELECT COUNT(*),COUNT(download_mbps) FROM measurements",
                [],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .unwrap();
        assert_eq!((count, nonnull), (6, 0));
        let versions: i64 = db
            .query_row(
                "SELECT COUNT(*) FROM schema_migrations WHERE version=2",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(versions, 1);
    }

    #[test]
    fn tool_target_rejects_shell_metacharacters() {
        assert!(safe_target("example.com"));
        assert!(safe_target("2001:db8::1"));
        assert!(!safe_target("example.com; whoami"));
        assert!(!safe_target("example.com/path"));
        assert!(!safe_target("-t"));
        assert!(!safe_target("a..b"));
    }

    #[test]
    fn disconnect_or_ssid_change_contaminates_measurement() {
        let original = NetworkSnapshot {
            interface_name: Some("Wi-Fi".into()),
            connection_type: "Wi-Fi".into(),
            local_ip: Some("192.168.1.2".into()),
            gateway: None,
            dns_servers: vec![],
            ssid: Some("Casa".into()),
            signal_percent: None,
            channel: None,
            network_id: Some("adapter-1".into()),
            capability_note: None,
        };
        let mut changed = original.clone();
        changed.ssid = Some("Outra".into());
        assert!(network_changed(&original, &changed));
        changed = original.clone();
        changed.local_ip = None;
        changed.network_id = None;
        assert!(network_changed(&original, &changed));
        assert!(!network_changed(&original, &original));
        let unknown = NetworkSnapshot {
            network_id: None,
            local_ip: None,
            ssid: None,
            ..original.clone()
        };
        assert!(network_changed(&unknown, &original));
    }
}
