# hello-docker

Konum bazlı alarm uygulaması. Haritadan bir hedef nokta ve uyarı mesafesi seçersin;
tarayıcı konumunu takip eder ve o noktaya yaklaştığında alarm çalar.

## Özellikler

- Haritaya tıklayarak veya adres arayarak hedef nokta seçme
- Ayarlanabilir uyarı mesafesi (20m - 2000m)
- Gerçek zamanlı konum takibi (Geolocation API)
- Hedefe girince sesli alarm ve tarayıcı bildirimi
- Seçilen hedef tarayıcıda saklanır (localStorage)

## Çalıştırma

### Docker ile

```bash
docker compose up --build
```

Ardından tarayıcıdan `http://localhost:8080` adresine git.

### Docker olmadan

`index.html` dosyasını statik bir sunucudan servis etmen yeterli (konum izni
için `localhost` veya HTTPS gerekir), örneğin:

```bash
npx serve .
```

## Notlar

- Konum takibi tarayıcı sekmesi açıkken çalışır; arka planda güvenilir takip
  için native bir mobil uygulama gerekir.
- Harita ve adres arama için OpenStreetMap / Nominatim servisleri kullanılır.
