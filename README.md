# TCGuard — Universal Crash, Exploit, Packet & Anti-Bot Protection for Minecraft

![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.8.8--1.21+-brightgreen.svg)
![Java](https://img.shields.io/badge/Java-8--21+-orange.svg)
![Release](https://img.shields.io/badge/Release-2.0--ENTERPRISE-gold.svg)

**TCGuard** is a high-performance, lightweight, all-in-one security and anti-exploit engine engineered for Minecraft servers (Spigot, Paper, Purpur, Folia across 1.8.8 to 1.21+). It neutralizes crashes, malicious packets, NBT exploits, botnet waves, and unauthorized permission escalations with zero TPS impact.

---

## 📌 Features Overview

### 1. 🛡️ Exploit & Crash Prevention
* **Log4J / JNDI Protection:** Blocks malicious JNDI lookups (CVE-2021-44228) in chat, commands, and signs.
* **NBT & Item Validation:** Drops illegal items, nested shulker boxes (shulker bombs), oversized books, and high-particle fireworks.
* **Movement & Position Checks:** Filters out `NaN` or `Infinity` coordinates and invalid pitch/yaw values that could crash chunks or clients.
* **Window & Inventory Protection:** Prevents invalid window click packets and illegal creative-mode item creation.

### 2. 🤖 Anti-Bot & Connection Shield
* **Handshake Timing Analysis:** Identifies automated bot software by measuring the time between connection handshake and login packets.
* **Subnet (/24) Rate Limiting:** Limits rapid joins originating from the same IP range.
* **Dynamic Attack Mode:** Temporarily tightens connection rules automatically when a join flood is detected.

### 3. ⚡ Packet & Combat Checks
* **Netty Exception Handling:** Traps corrupt network frames and closes broken sockets cleanly to prevent server tick hangs.
* **Auto-Clicker & Macro Detection:** Analyzes click standard deviation and CPS thresholds to block rapid macros (22+ CPS).
* **FastBreak & FastPlace:** Drops impossible block breaking and placement speeds.

### 4. 🔐 Admin Security, 2FA GuardCode & Auto-Deop Sync
* **Mandatory Admin GuardCode:** All OP and admin players must define and enter a secret GuardCode pin upon joining. Unverified admins are completely frozen and cannot move, run commands, or interact with blocks/inventories.
* **Console-Only Code Reset:** GuardCode PINs can strictly and exclusively be reset from the server console via `guardcode-reset <username>` or `guard resetcode <username>`. All in-game reset attempts are blocked.
* **Automatic Deop Synchronization:** Whenever an administrator is deopped (`/deop`, console `deop`, or server operator snapshot update), TCGuard instantly strips their authorized status and removes them from `admins.yml`.
* **Console-Only Whitelist (Zero-OP):** Having server `OP` does not grant access to TCGuard commands or bypass security checks. Administrators must be explicitly approved from the server console.
* **Staff IP Lock:** Optional restriction ensuring administrator accounts can only log in from approved IP addresses.
* **Brute-Force & Leaked Password Filter:** Limits repeated failed `/login` attempts and blocks common/weak passwords during registration.

### 5. 🗄️ Forensics & Database Logging
* **SQLite & MySQL Support:** Automatically logs security events asynchronously (zero TPS impact).
* **In-Game Audit Commands:** Check recent violations or a specific player's incident history with `/guard history <player>`.

### 6. 📊 In-Game GUI & Multi-Language Support
* **Interactive Dashboard:** Run `/guard gui` to see live TPS, RAM usage, and toggle individual protection modules.
* **Built-In Languages:** Supports **English (`en`)**, **Turkish (`tr`)**, and **Spanish (`es`)**. Switch languages directly in the GUI or via `config.yml`.

---

## 🎮 Commands & Permissions

### Console-Only Commands
These commands can only be executed directly from the server console:
| Command | Alias | Description |
| :--- | :--- | :--- |
| `guard-add <username>` | — | Adds a player to the authorized admin list (`admins.yml`). |
| `guard-remove <username>` | — | Removes a player from the authorized admin list (`admins.yml`). |
| `guard-list` | — | Lists all authorized admin usernames. |
| `guardcode-reset <username>` | `gcode-reset`, `resetcode`, `tcguardcode-reset` | Resets an administrator's GuardCode PIN. |
| `guard resetcode <username>` | `tcguard resetcode` | Resets an administrator's GuardCode PIN via main command. |

### In-Game Commands (Authorized Admins Only)
| Command | Alias | Description |
| :--- | :--- | :--- |
| `/guardcode set <pin>` | `/gcode set`, `/2fa set` | Sets your administrator GuardCode PIN. |
| `/guardcode <pin>` | `/gcode <pin>`, `/2fa <pin>` | Verifies your GuardCode PIN to unlock administrator session. |
| `/guard gui` | `/tcguard gui` | Opens the live interactive settings & performance dashboard. |
| `/guard reload` | `/tcguard reload` | Reloads configuration files, `admins.yml`, and translations. |
| `/guard stats` | `/tcguard stats` | Shows total blocked crashes, bots, and packet floods. |
| `/guard history <player>` | `/guard gecmis` | Shows security violation history for a specific player. |
| `/guard logs [limit]` | `/guard loglar` | Lists the most recent security events. |

---

## ⚙️ Configuration (`config.yml`)

```yaml
language: en

admin-guardcode:
  enabled: true
  max-failed-attempts: 5

auth-security:
  enabled: true
  brute-force:
    enabled: true
    max-attempts: 3
    lockout-minutes: 10
  password-strength:
    enabled: true
    min-length: 6
  admin-ip-lock:
    enabled: false
    trusted-ips:
      - 127.0.0.1

force-op-protection:
  enabled: true
  whitelisted-ops:
    - console

packet-protection:
  max-packets-per-second: 300
  max-custom-payload-size: 32767
  block-tab-complete: true
  block-creative-packets: true
  check-invalid-positions: true
  check-vehicle-movement: true
  check-window-clicks: true
  netty-pipeline-shield: true
  auto-clicker:
    enabled: true
    max-cps: 22
    min-standard-deviation: 2.2

anti-bot:
  enabled: true
  max-joins-per-ip-per-minute: 3
  max-global-joins-per-second: 5
  timing-protection:
    enabled: true
    min-handshake-duration-ms: 25
  subnet-protection:
    enabled: true
    max-joins-per-subnet-per-minute: 6
```

---

## 📦 Installation
1. Place `TCGuard.jar` (or `TCGuard-2.0-ENTERPRISE.jar`) in your server's `plugins/` directory.
2. (Optional) Install `ProtocolLib` for deeper packet inspection.
3. Start the server.
4. From your server console, add yourself as an authorized admin:
   ```
   guard-add YourUsername
   ```
5. Join the server and set your 2FA GuardCode PIN:
   ```
   /guardcode set 1234
   ```
6. Type `/guard gui` in-game to configure your modules!

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
