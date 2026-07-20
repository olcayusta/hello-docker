# Konum Bazlı Alarm — Android

`hello-docker` reposundaki web uygulamasının native Android karşılığı. Web sürümünün
temel kısıtı, tarayıcı sekmesi kapandığında konum takibinin durmasıydı; bu sürüm
Google Play services'in **Geofencing API**'siyle çalışır, dolayısıyla uygulama arka
plandayken veya son uygulamalar listesinden kapatıldığında da alarm tetiklenebilir.

## Özellikler

- Haritadan (osmdroid / OpenStreetMap, **API key gerekmez**) hedef nokta seçme veya
  Nominatim ile adres arama
- Ayarlanabilir uyarı mesafesi (20m–2000m)
- Sistem seviyesinde Geofencing ile arka planda güvenilir konum takibi
- Hedefe girince tam ekran alarm (kilit ekranı üzerinde), sesli siren (`AudioAttributes.USAGE_ALARM`
  ile sessiz moddan/DND'den etkilenmez) ve titreşim
- Cihaz yeniden başlatıldığında alarmın otomatik olarak yeniden kurulması

## ⚠️ Bu ortamda derlenip test edilmedi

Bu proje bir Android SDK/emülatörü olmayan bir sandbox'ta yazıldı, bu yüzden
derlenip çalıştırılamadı. Android Studio'da açıp derlemeniz ve gerçek bir cihazda
(veya Play services'li bir emülatörde) test etmeniz gerekiyor.

## Kurulum ve ilk çalıştırma kontrol listesi

1. Android Studio'da `android/` klasörünü proje olarak aç. Gradle sync sırasında
   IDE'nin önerdiği AGP/Kotlin sürüm güncellemelerini kabul et (wrapper jar'ı bu
   repoda yok — Studio ilk açılışta otomatik oluşturur/indirir).
2. `compileSdk`/`targetSdk` 35 SDK platformunun SDK Manager'dan kurulu olduğundan
   emin ol.
3. **Play services'li** bir emülatör image'i kullan (çıplak AOSP image'lerde
   Geofencing API çalışmaz) veya gerçek bir Android cihaz kullan.
4. Uygulamayı ilk açtığında izinleri sırasıyla ver:
   - Konum (tercihen "Tam konum" / Precise — küçük yarıçaplarda "Yaklaşık konum"
     yeterince hassas olmayabilir, uygulama bu durumda uyarı gösterir)
   - Android 10+ cihazlarda **arka planda konum**: sistem diyaloğu Android 11+'da
     "Her zaman izin ver" seçeneğini doğrudan sunmaz — uygulama sizi Ayarlar'a
     yönlendirecek, oradan Konum → "Her zaman izin ver" seçmeniz gerekiyor.
   - Bildirim izni (Android 13+)
   - Tam ekran alarm izni (Android 14+ bazı cihazlarda ayrıca açılması gerekebilir,
     uygulama bunu tespit edip ayarlara yönlendirme sunar)
5. Uygulama içindeki "Pil Optimizasyonunu Kapat" butonunu kullanarak bu uygulamayı
   pil optimizasyonundan muaf tut.
6. Küçük bir yarıçapla, emülatörün Extended Controls → Location → route simulation
   özelliğiyle sahte hareket vererek test et. Geofence `INITIAL_TRIGGER_ENTER` ile
   kurulduğu için, hedefin zaten yarıçap içindeyken "Takibi Başlat"a basarsan alarm
   hemen tetiklenmelidir.
7. **En önemli test:** Uygulamayı son uygulamalar listesinden kapatıp (swipe away)
   hedefe yaklaş — alarmın yine de çalması gerekiyor. Web sürümünün çözemediği
   asıl sorun buydu.

## Bilinen kısıtlar

- Geofencing, cihazda Google Play services gerektirir (Huawei'nin GMS'siz
  modelleri veya degoogled ROM'lar gibi cihazlarda çalışmaz).
- Geofence tetiklenmesi Android'de "best-effort"tür — Doze modu veya üretici
  bazlı agresif pil yönetimi (Xiaomi/MIUI, Huawei/EMUI, Samsung One UI, Oppo/ColorOS
  vb.) gecikmelere ya da uygulamanın tamamen kapatılmasına neden olabilir. Pil
  optimizasyonunu kapatmak yardımcı olur ama bu üreticilerin kendi
  "otomatik başlatma" / "korumalı uygulamalar" ayarlarını da kontrol etmeniz
  gerekebilir — bunlar uygulama içinden kontrol edilemez.
- Cihaz yeniden başlatıldığında sistem tüm geofence'leri temizler; bu uygulama
  `BOOT_COMPLETED` alıcısıyla otomatik olarak yeniden kurar (takip aktifse).
- Uygulama simgesi şu an bir sistem placeholder'ı (`ic_dialog_alert`) — gerçek bir
  simge için Android Studio'nun Image Asset aracını kullanman gerekiyor.

## Mimari özeti

- `MainActivity` — osmdroid harita, adres arama (Nominatim), yarıçap seçici, izin akışı
- `GeofenceManager` — `GeofencingClient` sarmalayıcısı (geofence ekleme/kaldırma)
- `GeofenceBroadcastReceiver` — geofence tetiklenince `AlarmService`'i başlatır
- `AlarmService` — ön planda servis: tam ekran bildirim + `MediaPlayer` ile siren +
  titreşim, `USAGE_ALARM` audio attribute'u ile sessiz moddan etkilenmez
- `AlarmActivity` — kilit ekranı üzerinde gösterilen tam ekran alarm arayüzü
- `BootCompletedReceiver` — cihaz yeniden başlayınca geofence'i yeniden kurar
- `PrefsRepository` — hedef konum/yarıçap/takip durumu için `SharedPreferences`
- `NominatimClient` — adres arama için Nominatim API istemcisi
