//! Versioned, local-only CSV representation for desktop observations.

pub const HEADER: &str = "schema_version,record_type,execution_id,started_at_ms,captured_at_ms,status,download_mbps,upload_mbps,latency_ms,jitter_ms,packet_loss_percent,packet_loss_source,bufferbloat_ms,dns_ms,network_id,ssid,local_ip,limitation,phase_details_json,phase_errors\r\n";

pub fn cell(value: &str) -> String {
    // Spreadsheet programs can evaluate formulas even inside quoted CSV cells.
    let starts_formula = value
        .trim_start_matches(|c: char| c.is_control() || c.is_whitespace())
        .starts_with(['=', '+', '-', '@']);
    let safe = if starts_formula {
        format!("'{value}")
    } else {
        value.to_string()
    };
    format!("\"{}\"", safe.replace('"', "\"\""))
}

pub fn line(values: &[String]) -> String {
    let mut output = values
        .iter()
        .map(|value| cell(value))
        .collect::<Vec<_>>()
        .join(",");
    output.push_str("\r\n");
    output
}

pub fn number(value: Option<f64>) -> String {
    value.map(|v| v.to_string()).unwrap_or_default()
}
pub fn text(value: Option<&str>) -> String {
    value.unwrap_or_default().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn quotes_and_neutralizes_network_controlled_fields() {
        assert_eq!(cell("=HYPERLINK(\"bad\")"), "\"'=HYPERLINK(\"\"bad\"\")\"");
        assert_eq!(cell("\r\n+SUM(1)"), "\"'\r\n+SUM(1)\"");
        assert_eq!(cell("Casa, 5G"), "\"Casa, 5G\"");
    }

    #[test]
    fn absent_number_is_empty_and_not_zero() {
        assert_eq!(number(None), "");
    }

    #[test]
    fn replacement_of_existing_export_path_works_on_this_host() {
        let root = std::env::temp_dir();
        let stem = uuid::Uuid::new_v4().to_string();
        let old = root.join(format!("signallq-export-{stem}.csv"));
        let new = root.join(format!("signallq-export-{stem}.tmp"));
        std::fs::write(&old, "old").unwrap();
        std::fs::write(&new, "new").unwrap();
        let replacement = std::fs::rename(&new, &old);
        let contents = std::fs::read_to_string(&old);
        let _ = std::fs::remove_file(&old);
        let _ = std::fs::remove_file(&new);
        replacement.unwrap();
        assert_eq!(contents.unwrap(), "new");
    }
}
