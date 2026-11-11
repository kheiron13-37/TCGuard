# 4.3.0 → 5.0.0: ne değişti

Sonraki sürümler için [CHANGELOG.md](../CHANGELOG.md) ve [sürüm notları](releases). Dosya yolları
5.1.0 itibarıyla `plugin/` altında. Bu sayfa 5.0.0 anını anlatır; 6.0.0'da değişen iki satır
aşağıda işaretlidir.

Kaynak ağacı baştan düzenlendi. Eski `net.tcguard.modules`, `config`, `api`, `util`, `security` ve
`bukkit` paketleri kalktı; her şey tek pakette (`net.tcguard`) 10 dosya.

| | 4.3.0 | 5.0.0 |
| --- | --- | --- |
| Kaynak dosyası | 59 | 10 |
| Satır | 10.679 | 1.377 |
| `config.yml` | 524 satır | 97 satır |
| Jar | 13 MB (SQLite gömülü) | 46 KB |

## Yeni dosyalar ve eski karşılıkları

| 5.0.0 | Ne yapar | 4.3.0'da neydi |
| --- | --- | --- |
| `TCGuard.java` | Açılış, config, `/tcguard` komutu, uyarı ve kick | `TCGuard.java` (798 satır → 202) |
| `NettyGuard.java` | Her bağlantıya kabul anında denetleyici takar | `NettyPipelineGuard.java` |
| `FrameInspector.java` | Ham çerçeve ve NBT taraması | `RawPacketInspector.java` |
| `PacketFilter.java` | Çözülmüş paket kontrolleri ve paket hızı | `PacketGuard.java` (634 satır → 133) |
| | *6.0.0: bu dosya ve ProtocolLib bağımlılığı kalktı; kontroller `FrameInspector` ve `NettyGuard` içinde* | |
| `Violations.java` | İhlal puanı (VL) motoru | `ViolationManager.java` |
| `ItemSanitizer.java` | Creative eşyayı yeniden kurar, büyü/yığın düzeltir | `CreativeItemSanitizer`, `NbtValidator`, `AntiIllegalGuard` |
| `WorldPatches.java` | Dünya mekaniği yamaları | `PatcherGuard`, `WorldCrashGuard`, `MechanicalGuard` (üçü aynı yamaları tekrar ediyordu) |
| `CommandFilter.java` | Eklenti exploit komutları | `HoneypotGuard.java` |
| `Log4jFilter.java` | Log4Shell tespiti ve log filtresi | `Log4jShield.java` |
| `OpGuard.java` | Op izin listesi | `ForceOPGuard.java` |

## Silinen dosyalar

| Dosya | Neden |
| --- | --- |
| `ClickAnalysisGuard`, `BehavioralEngine`, `FastInteractGuard`, `ClientBrandScanner` | Anti-cheat işi; bu eklentinin konusu değil |
| `AntiBotManager`, `AntiBotChallengeGuard`, `ConnectionFingerprint`, `GeoIPShield`, `ThreatIntelligence`, `NetUtil` | Anti-bot proxy'de yapılmalı |
| `GuardCodeManager`, `AdminManager`, `AuthSecurityGuard`, `BypassManager`, `PermissionIntegrityGuard`, `security/auth/*` | Giriş güvenliği ayrı bir eklentinin işi; bypass artık `tcguard.bypass` izni |
| `EventGuard` | Sohbet/komut limiti ve eklenti listesi gizleme |
| `LagGuard`, `SmartLagGuard`, `ChunkLagGuard` | Sadece kısıtlayan lag limitleri |
| `NoComCoordShield` | Adı NoCom'du ama ışınlanan oyuncuyu kısa süre gizliyordu; gerçek bir açığı kapatmıyordu |
| `EconomyIntegrityGuard` | Kapsam dışı |
| `StatsManager`, `PlayerProfiler`, `DatabaseService`, `DatabaseManager`, `DiscordWebhook`, `GuardGui` | Kayıt, profil, arayüz |
| `ProxyBridge*`, `BridgeCodec`, `RateLimiter`, `ScanBudget`, `AtomicYaml` | Kullanan kod kalmadı |
| `ConfigManager`, `LanguageManager`, `ColorUtil`, `Compatibility`, `TCGuardAPI`, `TCGuardViolationEvent` | Config doğrudan okunuyor; mesajlar `config.yml` içinde; yalnız 1.8 desteklendiği için reflection katmanı gereksiz |

## Davranış değişiklikleri

- **Denetleyici bağlantı anında takılıyor.** 4.3.0'da `PlayerJoinEvent` beklenirdi; arada gelen ilk
  paketler denetlenmezdi.
- **Sıkıştırma bombası.** Açılınca en büyük çerçeve sınırını aşacağını bildiren sıkıştırılmış
  çerçeve açılmadan reddedilir. 4.3.0 bunu ancak açtıktan sonra görüyordu.
- **Çerçeve sınırı paket türüne göre.** Eşyasız paket 8 KB, eşya taşıyan paket 64 KB. 4.3.0'daki tek
  12 KB sınırı dolu kitabı tıklayan oyuncuyu atıyordu.
- **Geçersiz çerçeve bağlantıyı kapatır.** 4.3.0 önce 100 VL ekleyip eşik eylemini beklerdi.
- **Creative kara listesi.** Varsayılan artık `COMMAND, COMMAND_MINECART`. Portal blokları listeden
  çıktı; Spigot bunları zaten creative'de vermiyor.
- **Survival eşya düzeltmesi.** Sadece `items.max-enchant-level` (varsayılan 10) üstündeki büyüler ve
  fazla yığınlar düzeltilir. Silk Touch + Fortune ve Unbreakable müdahaleleri kalktı.
- **Komut filtresi.** Liste tamamen config'de (`commands.blocked`). `/worldedit:/calc` gibi namespace
  ile yazılan komut da yakalanır. `/pl` ve `:` içeren komutların engellenmesi kalktı.
- **VL eylemleri.** Biçim `"<seviye> <eylem>"` listesi oldu. Yer tutucular: `%player% %uuid% %check% %vls%`.
- **Uyarılar.** `tcguard.alerts` iznine sahip oyunculara ve konsola gider.
- **Sürüm.** 1.8 dışında eklenti kendini kapatır. ProtocolLib zorunlu. *(6.0.0: eklenti 1.8 - 1.21
  arasında çalışır, ProtocolLib gerekmez.)*

## Kod üslubu

- Yorumlar yalnız kodun kendisinden anlaşılmayan şeyi (bir kararın nedenini) söylüyor.
- `catch (Throwable ignored)` blokları kalktı; hata ya ele alınıyor ya da loglanıyor.
- Aynı yamayı üç ayrı sınıfta tekrar eden kod birleştirildi.
- Her ayar için ayrı getter üreten `ConfigManager` yerine config doğrudan okunuyor.
