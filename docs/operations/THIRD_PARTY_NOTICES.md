---
title: "Third-Party Notices"
description: "Licenças de bibliotecas de terceiros usadas pelo módulo featureDevices."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Third-Party Notices

- **Escopo:** licenças de terceiros usadas pelo módulo `featureDevices`

Este arquivo lista as bibliotecas de código aberto utilizadas pelo SignallQ com suas
respectivas licenças e informações de atribuição.

---

## AndroidNetworkTools

- **Autor:** Stephan Cilliers (stealthcopter)
- **Repositório:** https://github.com/stealthcopter/AndroidNetworkTools
- **Versão em uso:** 0.4.5.3
- **Licença:** Apache License, Version 2.0

### Uso no SignallQ

Utilizado pelo módulo `featureDevices` para:
- Descoberta de hosts via ping nativo (`SubnetDevices`) — substitui `InetAddress.isReachable()` que não funciona sem root em Android 10+.
- Lookup de MAC via cache ARP (`ARPInfo.getMacFromIPAddress()`).

### Texto da Licença Apache 2.0

```
Copyright 2016 Stephan Cilliers

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Texto completo disponível em: https://www.apache.org/licenses/LICENSE-2.0

---

---

## jmDNS

- **Autores:** Arthur van Hoff, Rick Blair, Jeff Sonstein, Werner Randelshofer, Pierre Frisch, Scott Lewis, Victor Toni e contribuidores
- **Repositório:** https://github.com/jmdns/jmdns
- **Versão em uso:** 3.6.3
- **Licença:** Apache License, Version 2.0

### Uso no SignallQ

Utilizado pelo módulo `featureDevices` para descoberta mDNS/Bonjour robusta:
- Enumera serviços `_googlecast`, `_airplay`, `_ipp`, `_printer`, `_smb`, `_ssh`, `_homekit`, `_hap`, `_amzn-wplay` e outros via multicast IPv4.
- Extrai nomes amigáveis de TXT records (`fn=`, `md=`, `model=`, `ty=`) de Chromecast, AirPlay, impressoras e similares.
- Substitui o parser de pacotes DNS binários artesanal, eliminando fragilidade em payloads não-padrão.

### Texto da Licença Apache 2.0

```
Copyright 2003 Arthur van Hoff, Rick Blair

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Texto completo disponível em: https://www.apache.org/licenses/LICENSE-2.0

---

## OkHttp

- **Autor:** Square, Inc.
- **Repositório:** https://github.com/square/okhttp
- **Versão em uso:** 4.12.0
- **Licença:** Apache License, Version 2.0

### Uso no SignallQ

Utilizado pelo módulo `featureDevices` para:
- Fetch do XML de descrição UPnP (`LOCATION` header das respostas SSDP M-SEARCH) para extrair `<friendlyName>`, `<manufacturer>` e `<modelName>` de dispositivos smart home.

### Texto da Licença Apache 2.0

```
Copyright 2019 Square, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Texto completo disponível em: https://www.apache.org/licenses/LICENSE-2.0

---

*Última atualização: 2026-07-23*
