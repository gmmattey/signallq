use super::NetworkSnapshot;

#[cfg(target_os = "windows")]
mod platform {
    use super::NetworkSnapshot;
    use std::{
        ffi::c_void,
        net::{IpAddr, Ipv4Addr, Ipv6Addr},
        ptr,
    };
    use windows_sys::Win32::{
        NetworkManagement::{
            IpHelper::{
                GetAdaptersAddresses, GetBestInterfaceEx, GAA_FLAG_INCLUDE_GATEWAYS,
                IF_TYPE_IEEE80211, IP_ADAPTER_ADDRESSES_LH,
            },
            Ndis::IfOperStatusUp,
            WiFi::{
                wlan_interface_state_connected, wlan_intf_opcode_current_connection,
                WlanCloseHandle, WlanEnumInterfaces, WlanFreeMemory, WlanOpenHandle,
                WlanQueryInterface, WLAN_CONNECTION_ATTRIBUTES, WLAN_INTERFACE_INFO_LIST,
            },
        },
        Networking::WinSock::{
            AF_INET, AF_INET6, AF_UNSPEC, SOCKADDR, SOCKADDR_IN, SOCKET_ADDRESS,
        },
    };

    fn wide_string(value: *const u16) -> String {
        if value.is_null() {
            return String::new();
        }
        let len = unsafe {
            (0..1024)
                .take_while(|offset| *value.add(*offset) != 0)
                .count()
        };
        String::from_utf16_lossy(unsafe { std::slice::from_raw_parts(value, len) })
    }

    fn socket_ip(value: SOCKET_ADDRESS) -> Option<IpAddr> {
        if value.lpSockaddr.is_null() {
            return None;
        }
        let size = value.iSockaddrLength as usize;
        let family = unsafe { (*value.lpSockaddr).sa_family };
        if family == AF_INET && size >= 8 {
            let bytes = unsafe { (value.lpSockaddr as *const u8).add(4) };
            return Some(IpAddr::V4(Ipv4Addr::from(unsafe {
                *(bytes as *const [u8; 4])
            })));
        }
        if family == AF_INET6 && size >= 24 {
            let bytes = unsafe { (value.lpSockaddr as *const u8).add(8) };
            return Some(IpAddr::V6(Ipv6Addr::from(unsafe {
                *(bytes as *const [u8; 16])
            })));
        }
        None
    }

    fn wifi_details(description: &str) -> (Option<String>, Option<u8>, Option<String>) {
        let mut version = 0u32;
        let mut handle = ptr::null_mut();
        if unsafe { WlanOpenHandle(2, ptr::null(), &mut version, &mut handle) } != 0 {
            return (None, None, None);
        }
        let mut list: *mut WLAN_INTERFACE_INFO_LIST = ptr::null_mut();
        let mut answer = (None, None, None);
        if unsafe { WlanEnumInterfaces(handle, ptr::null(), &mut list) } == 0 && !list.is_null() {
            let count = unsafe { (*list).dwNumberOfItems.min(64) as usize };
            let first = unsafe { (*list).InterfaceInfo.as_ptr() };
            for index in 0..count {
                let interface = unsafe { &*first.add(index) };
                let name = wide_string(interface.strInterfaceDescription.as_ptr());
                if interface.isState != wlan_interface_state_connected
                    || !name.eq_ignore_ascii_case(description)
                {
                    continue;
                }
                let mut size = 0u32;
                let mut data: *mut c_void = ptr::null_mut();
                let mut opcode_type = 0i32;
                if unsafe {
                    WlanQueryInterface(
                        handle,
                        &interface.InterfaceGuid,
                        wlan_intf_opcode_current_connection,
                        ptr::null(),
                        &mut size,
                        &mut data,
                        &mut opcode_type,
                    )
                } == 0
                    && !data.is_null()
                    && size as usize >= std::mem::size_of::<WLAN_CONNECTION_ATTRIBUTES>()
                {
                    let attributes = unsafe { &*(data as *const WLAN_CONNECTION_ATTRIBUTES) };
                    let ssid = &attributes.wlanAssociationAttributes.dot11Ssid;
                    let len = ssid.uSSIDLength.min(32) as usize;
                    let bssid = attributes.wlanAssociationAttributes.dot11Bssid;
                    answer = (
                        Some(String::from_utf8_lossy(&ssid.ucSSID[..len]).into_owned()),
                        Some(
                            attributes
                                .wlanAssociationAttributes
                                .wlanSignalQuality
                                .min(100) as u8,
                        ),
                        Some(format!(
                            "wifi-bssid:{:02x}:{:02x}:{:02x}:{:02x}:{:02x}:{:02x}",
                            bssid[0], bssid[1], bssid[2], bssid[3], bssid[4], bssid[5]
                        )),
                    );
                }
                if !data.is_null() {
                    unsafe { WlanFreeMemory(data) };
                }
                break;
            }
        }
        if !list.is_null() {
            unsafe { WlanFreeMemory(list as *const c_void) };
        }
        unsafe { WlanCloseHandle(handle, ptr::null()) };
        answer
    }

    pub fn snapshot_for(destination: Ipv4Addr) -> NetworkSnapshot {
        let mut route = SOCKADDR_IN {
            sin_family: AF_INET,
            ..Default::default()
        };
        route.sin_addr.S_un.S_addr = u32::from_ne_bytes(destination.octets());
        let mut best_index = 0u32;
        let route_known = unsafe {
            GetBestInterfaceEx(
                &route as *const SOCKADDR_IN as *const SOCKADDR,
                &mut best_index,
            )
        } == 0;
        let mut size = 16_384u32;
        let mut buffer = vec![0u64; (size as usize).div_ceil(8)];
        let mut result = unsafe {
            GetAdaptersAddresses(
                AF_UNSPEC as u32,
                GAA_FLAG_INCLUDE_GATEWAYS,
                ptr::null(),
                buffer.as_mut_ptr() as *mut IP_ADAPTER_ADDRESSES_LH,
                &mut size,
            )
        };
        if result == 111 {
            buffer.resize((size as usize).div_ceil(8), 0);
            result = unsafe {
                GetAdaptersAddresses(
                    AF_UNSPEC as u32,
                    GAA_FLAG_INCLUDE_GATEWAYS,
                    ptr::null(),
                    buffer.as_mut_ptr() as *mut IP_ADAPTER_ADDRESSES_LH,
                    &mut size,
                )
            };
        }
        if result != 0 {
            return NetworkSnapshot {
                interface_name: None,
                connection_type: "Indisponível".into(),
                local_ip: None,
                gateway: None,
                dns_servers: vec![],
                ssid: None,
                signal_percent: None,
                channel: None,
                network_id: None,
                capability_note: Some(format!(
                    "A API de adaptadores do Windows retornou erro {result}."
                )),
            };
        }
        let mut current = buffer.as_ptr() as *const IP_ADAPTER_ADDRESSES_LH;
        let mut selected: Option<(u32, NetworkSnapshot)> = None;
        for _ in 0..128 {
            if current.is_null() {
                break;
            }
            let adapter = unsafe { &*current };
            current = adapter.Next;
            if adapter.OperStatus != IfOperStatusUp || adapter.IfType == 24 {
                continue;
            }
            let interface_index = unsafe { adapter.Anonymous1.Anonymous.IfIndex };
            if route_known && interface_index != best_index {
                continue;
            }
            let mut local_ip = None;
            let mut address = adapter.FirstUnicastAddress;
            for _ in 0..64 {
                if address.is_null() {
                    break;
                }
                let item = unsafe { &*address };
                if let Some(ip) = socket_ip(item.Address) {
                    if ip.is_ipv4() && !ip.is_loopback() {
                        local_ip = Some(ip.to_string());
                        break;
                    }
                }
                address = item.Next;
            }
            let mut gateway = None;
            let mut gate = adapter.FirstGatewayAddress;
            for _ in 0..16 {
                if gate.is_null() {
                    break;
                }
                let item = unsafe { &*gate };
                if let Some(ip) = socket_ip(item.Address) {
                    if ip.is_ipv4() {
                        gateway = Some(ip.to_string());
                        break;
                    }
                }
                gate = item.Next;
            }
            if local_ip.is_none() {
                continue;
            }
            let mut dns_servers = vec![];
            let mut dns = adapter.FirstDnsServerAddress;
            for _ in 0..16 {
                if dns.is_null() {
                    break;
                }
                let item = unsafe { &*dns };
                if let Some(ip) = socket_ip(item.Address) {
                    dns_servers.push(ip.to_string());
                }
                dns = item.Next;
            }
            let description = wide_string(adapter.Description);
            let is_wifi = adapter.IfType == IF_TYPE_IEEE80211;
            let (ssid, signal_percent, wifi_network_id) = if is_wifi {
                wifi_details(&description)
            } else {
                (None, None, None)
            };
            let adapter_id = unsafe { std::ffi::CStr::from_ptr(adapter.AdapterName as *const i8) }
                .to_string_lossy();
            let network_id = if !route_known {
                None
            } else if is_wifi {
                wifi_network_id.or_else(|| ssid.as_ref().map(|name| format!("wifi-ssid:{name}")))
            } else {
                gateway
                    .as_ref()
                    .map(|gate| format!("ethernet-adapter:{adapter_id}:gateway:{gate}"))
            };
            let candidate = NetworkSnapshot {
                interface_name: Some(wide_string(adapter.FriendlyName)),
                connection_type: if is_wifi { "Wi-Fi".into() } else if adapter.IfType == 6 { "Ethernet".into() } else { format!("Adaptador ({})", adapter.IfType) },
                local_ip, gateway, dns_servers, ssid, signal_percent, channel: None,
                network_id,
                capability_note: Some(if route_known {
                    if is_wifi { "Adaptador escolhido pela rota do Windows ao destino de referência. Canal indisponível; SSID e sinal dependem do serviço WLAN e do driver." }
                    else { "Adaptador escolhido pela rota do Windows ao destino de referência." }
                } else { "Rota ao destino indisponível; adaptador escolhido pela métrica IPv4, sem vínculo garantido à medição." }.into()),
            };
            let score = (if candidate.gateway.is_some() {
                0
            } else {
                1_000_000
            }) + adapter.Ipv4Metric;
            if selected.as_ref().is_none_or(|(best, _)| score < *best) {
                selected = Some((score, candidate));
            }
        }
        selected.map(|(_, value)| value).unwrap_or(NetworkSnapshot {
            interface_name: None,
            connection_type: "Sem adaptador ativo".into(),
            local_ip: None,
            gateway: None,
            dns_servers: vec![],
            ssid: None,
            signal_percent: None,
            channel: None,
            network_id: None,
            capability_note: Some(
                "O Windows não informou um adaptador ativo com IPv4 local na rota selecionada."
                    .into(),
            ),
        })
    }
}

#[cfg(target_os = "windows")]
pub fn snapshot_for(destination: std::net::Ipv4Addr) -> NetworkSnapshot {
    platform::snapshot_for(destination)
}

#[cfg(target_os = "windows")]
pub fn snapshot() -> NetworkSnapshot {
    snapshot_for(std::net::Ipv4Addr::new(1, 1, 1, 1))
}

#[cfg(not(target_os = "windows"))]
pub fn snapshot() -> NetworkSnapshot {
    NetworkSnapshot {
        interface_name: None,
        connection_type: "Indisponível neste sistema".into(),
        local_ip: None,
        gateway: None,
        dns_servers: vec![],
        ssid: None,
        signal_percent: None,
        channel: None,
        network_id: None,
        capability_note: Some("Esta coleta usa as APIs de adaptadores do Windows.".into()),
    }
}

#[cfg(not(target_os = "windows"))]
pub fn snapshot_for(_destination: std::net::Ipv4Addr) -> NetworkSnapshot {
    snapshot()
}
