const STORAGE_KEY = "location-alarm-target";

const map = L.map("map").setView([41.0082, 28.9784], 13);
L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution: "&copy; OpenStreetMap contributors",
    maxZoom: 19,
}).addTo(map);

let targetMarker = null;
let radiusCircle = null;
let userMarker = null;
let target = null;
let watchId = null;
let alarmActive = false;
let audioCtx = null;
let alarmInterval = null;

const radiusInput = document.getElementById("radius");
const radiusValueEl = document.getElementById("radiusValue");
const targetLabelEl = document.getElementById("targetLabel");
const startBtn = document.getElementById("startBtn");
const stopBtn = document.getElementById("stopBtn");
const statusEl = document.getElementById("status");
const searchInput = document.getElementById("search");
const searchBtn = document.getElementById("searchBtn");
const alarmOverlay = document.getElementById("alarmOverlay");
const alarmDistanceEl = document.getElementById("alarmDistance");
const dismissBtn = document.getElementById("dismissBtn");

function haversineDistance(lat1, lon1, lat2, lon2) {
    const R = 6371000;
    const toRad = (deg) => (deg * Math.PI) / 180;
    const dLat = toRad(lat2 - lat1);
    const dLon = toRad(lon2 - lon1);
    const a =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(a));
}

function setTarget(lat, lng, save = true) {
    target = { lat, lng, radius: Number(radiusInput.value) };

    if (targetMarker) {
        targetMarker.setLatLng([lat, lng]);
    } else {
        targetMarker = L.marker([lat, lng], { draggable: true }).addTo(map);
        targetMarker.on("dragend", () => {
            const pos = targetMarker.getLatLng();
            setTarget(pos.lat, pos.lng);
        });
    }

    if (radiusCircle) {
        radiusCircle.setLatLng([lat, lng]);
        radiusCircle.setRadius(target.radius);
    } else {
        radiusCircle = L.circle([lat, lng], {
            radius: target.radius,
            color: "#3b82f6",
            fillOpacity: 0.15,
        }).addTo(map);
    }

    targetLabelEl.textContent = `Hedef: ${lat.toFixed(5)}, ${lng.toFixed(5)}`;
    startBtn.disabled = false;

    if (save) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(target));
    }
}

map.on("click", (e) => {
    setTarget(e.latlng.lat, e.latlng.lng);
});

radiusInput.addEventListener("input", () => {
    radiusValueEl.textContent = radiusInput.value;
    if (target) {
        setTarget(target.lat, target.lng);
    }
});

searchBtn.addEventListener("click", async () => {
    const query = searchInput.value.trim();
    if (!query) return;
    statusEl.textContent = "Aranıyor...";
    try {
        const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(query)}`;
        const res = await fetch(url);
        const results = await res.json();
        if (!results.length) {
            statusEl.textContent = "Sonuç bulunamadı.";
            return;
        }
        const { lat, lon } = results[0];
        map.setView([lat, lon], 15);
        setTarget(Number(lat), Number(lon));
        statusEl.textContent = "Hedef adres bulundu ve işaretlendi.";
    } catch (err) {
        statusEl.textContent = "Arama başarısız oldu.";
    }
});

function playAlarmSound() {
    if (!audioCtx) {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = "sine";
    osc.frequency.setValueAtTime(880, audioCtx.currentTime);
    osc.frequency.linearRampToValueAtTime(440, audioCtx.currentTime + 0.5);
    gain.gain.setValueAtTime(0.3, audioCtx.currentTime);
    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start();
    osc.stop(audioCtx.currentTime + 0.5);
}

function startAlarm(distance) {
    if (alarmActive) return;
    alarmActive = true;
    alarmOverlay.classList.remove("hidden");
    alarmDistanceEl.textContent = `Hedefe mesafe: ${Math.round(distance)} m`;
    playAlarmSound();
    alarmInterval = setInterval(playAlarmSound, 700);

    if (Notification.permission === "granted") {
        new Notification("Hedefe ulaştın!", {
            body: "Belirlediğin konuma yaklaştın.",
        });
    }
}

function stopAlarm() {
    alarmActive = false;
    alarmOverlay.classList.add("hidden");
    if (alarmInterval) {
        clearInterval(alarmInterval);
        alarmInterval = null;
    }
}

dismissBtn.addEventListener("click", stopAlarm);

function handlePosition(position) {
    const { latitude, longitude } = position.coords;

    if (userMarker) {
        userMarker.setLatLng([latitude, longitude]);
    } else {
        userMarker = L.marker([latitude, longitude], {
            icon: L.divIcon({ className: "user-dot", html: "🔵" }),
        }).addTo(map);
    }

    if (!target) return;

    const distance = haversineDistance(latitude, longitude, target.lat, target.lng);
    statusEl.textContent = `Takip ediliyor... Hedefe mesafe: ${Math.round(distance)} m`;

    if (distance <= target.radius) {
        startAlarm(distance);
    } else {
        stopAlarm();
    }
}

function handlePositionError(err) {
    statusEl.textContent = `Konum alınamadı: ${err.message}`;
}

startBtn.addEventListener("click", () => {
    if (!target || !navigator.geolocation) return;

    if (Notification.permission === "default") {
        Notification.requestPermission();
    }

    watchId = navigator.geolocation.watchPosition(handlePosition, handlePositionError, {
        enableHighAccuracy: true,
        maximumAge: 0,
        timeout: 15000,
    });

    startBtn.disabled = true;
    stopBtn.disabled = false;
    statusEl.textContent = "Takip başlatıldı...";
});

stopBtn.addEventListener("click", () => {
    if (watchId !== null) {
        navigator.geolocation.clearWatch(watchId);
        watchId = null;
    }
    stopAlarm();
    startBtn.disabled = false;
    stopBtn.disabled = true;
    statusEl.textContent = "Takip durduruldu.";
});

const saved = localStorage.getItem(STORAGE_KEY);
if (saved) {
    const parsed = JSON.parse(saved);
    radiusInput.value = parsed.radius;
    radiusValueEl.textContent = parsed.radius;
    setTarget(parsed.lat, parsed.lng, false);
    map.setView([parsed.lat, parsed.lng], 15);
}

if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
        (position) => {
            if (!saved) {
                map.setView([position.coords.latitude, position.coords.longitude], 14);
            }
        },
        () => {},
    );
}
