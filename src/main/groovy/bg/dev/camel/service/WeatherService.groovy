package bg.dev.camel.service

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.stereotype.Service

@Service
class WeatherService {

  private static final Map<Integer, String> WMO = [
    0: 'Clear sky', 1: 'Mainly clear', 2: 'Partly cloudy', 3: 'Overcast',
    45: 'Fog', 48: 'Icy fog',
    51: 'Light drizzle', 53: 'Moderate drizzle', 55: 'Dense drizzle',
    61: 'Slight rain', 63: 'Moderate rain', 65: 'Heavy rain',
    71: 'Slight snow', 73: 'Moderate snow', 75: 'Heavy snow',
    80: 'Slight showers', 81: 'Moderate showers', 82: 'Violent showers',
    85: 'Slight snow showers', 86: 'Heavy snow showers',
    95: 'Thunderstorm', 96: 'Thunderstorm with hail', 99: 'Thunderstorm with heavy hail'
  ]

  /**
   * Fetches weather from Open-Meteo (no API key required).
   * startDate / endDate are ISO strings (YYYY-MM-DD). When both are null, returns current conditions only.
   */
  static String checkWeather(String city, String startDate, String endDate) {
    def slurper = new JsonSlurper()

    // ── Step 1: Geocode city name → latitude / longitude ─────────────────
    def encodedCity = URLEncoder.encode(city, 'UTF-8')
    def geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${encodedCity}&count=1&language=en&format=json"
    def geo = slurper.parseText(new URI(geoUrl).toURL().text)

    if (!geo.results) {
      return JsonOutput.toJson([error: "City not found: ${city}"])
    }

    def loc = geo.results[0]
    double lat = loc.latitude as double
    double lon = loc.longitude as double
    String locationLabel = "${loc.name}${loc.country ? ', ' + loc.country : ''}"

    // ── Step 2: Fetch weather ─────────────────────────────────────────────
    boolean isForecast = startDate != null && !startDate.isBlank()
    String end = (endDate != null && !endDate.isBlank()) ? endDate : startDate

    String params = "latitude=${lat}&longitude=${lon}&timezone=auto"
    if (isForecast) {
      params += "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_sum"
      params += "&start_date=${startDate}&end_date=${end}"
    } else {
      params += "&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m"
    }

    def weather = slurper.parseText(new URL("https://api.open-meteo.com/v1/forecast?${params}").text)

    // ── Step 3: Format response ───────────────────────────────────────────
    def result = [location: locationLabel]

    if (isForecast) {
      def daily = weather.daily
      result.forecast = daily.time.withIndex().collect { String date, int i ->
        [
          date          : date,
          condition     : WMO.get(daily.weather_code[i] as int, 'Unknown'),
          tempMin       : "${daily.temperature_2m_min[i]}°C",
          tempMax       : "${daily.temperature_2m_max[i]}°C",
          precipitation : "${daily.precipitation_sum[i] ?: 0} mm"
        ]
      }
    } else {
      def cur = weather.current
      result.current = [
        temperature : "${cur.temperature_2m}°C",
        condition   : WMO.get(cur.weather_code as int, 'Unknown'),
        windSpeed   : "${cur.wind_speed_10m} km/h",
        humidity    : "${cur.relative_humidity_2m}%"
      ]
    }

    JsonOutput.toJson(result)
  }
}