package com.fitouts.shared.geocode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.shared.error.BadRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GeocodeService {

    private static final Pattern MAPS_HOST =
            Pattern.compile("(^|\\.)((maps\\.google\\.[\\w.]+)|(goo\\.gl)|(maps\\.app\\.goo\\.gl))", Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_COORDS =
            Pattern.compile("@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern D3D4 =
            Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern Q_COORDS =
            Pattern.compile("[?&]q=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LL_COORDS =
            Pattern.compile("[?&]ll=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public GeocodeResult resolve(String query) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException("Location query is required");
        }
        String trimmed = query.trim();

        if (trimmed.matches("-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?")) {
            String[] parts = trimmed.split(",");
            double lat = parseDouble(parts[0].trim());
            double lng = parseDouble(parts[1].trim());
            GeocodeResult reverse = reverseNominatim(lat, lng);
            reverse.setMapsShareUrl(buildGoogleShareUrl(lat, lng, reverse.getDisplayName()));
            return reverse;
        }

        if (isMapsUrl(trimmed)) {
            String expanded = expandUrl(trimmed);
            Coordinates coords = parseGoogleCoordinates(expanded);
            if (coords != null) {
                GeocodeResult reverse = reverseNominatim(coords.lat(), coords.lng());
                reverse.setMapsShareUrl(expanded);
                return reverse;
            }
            String qParam = extractQueryParam(expanded, "q");
            if (StringUtils.hasText(qParam) && !Q_COORDS.matcher("q=" + qParam).find()) {
                GeocodeResult searched = searchNominatim(qParam);
                searched.setMapsShareUrl(expanded);
                return searched;
            }
            throw new BadRequestException("Could not extract coordinates from the Google Maps link");
        }
        GeocodeResult searched = searchNominatim(trimmed);
        if (searched.getLatitude() != null && searched.getLongitude() != null) {
            searched.setMapsShareUrl(buildGoogleShareUrl(
                    searched.getLatitude().doubleValue(),
                    searched.getLongitude().doubleValue(),
                    searched.getDisplayName()));
        }
        return searched;
    }

    private boolean isMapsUrl(String value) {
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return host != null && MAPS_HOST.matcher(host).find();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String expandUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "FitOuts/1.0 (site-visit-geocode)")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.uri().toString();
        } catch (Exception ex) {
            throw new BadRequestException("Unable to open the maps link: " + ex.getMessage());
        }
    }

    private Coordinates parseGoogleCoordinates(String url) {
        Matcher d3d4 = D3D4.matcher(url);
        if (d3d4.find()) {
            return new Coordinates(parseDouble(d3d4.group(1)), parseDouble(d3d4.group(2)));
        }
        Matcher at = AT_COORDS.matcher(url);
        if (at.find()) {
            return new Coordinates(parseDouble(at.group(1)), parseDouble(at.group(2)));
        }
        Matcher q = Q_COORDS.matcher(url);
        if (q.find()) {
            return new Coordinates(parseDouble(q.group(1)), parseDouble(q.group(2)));
        }
        Matcher ll = LL_COORDS.matcher(url);
        if (ll.find()) {
            return new Coordinates(parseDouble(ll.group(1)), parseDouble(ll.group(2)));
        }
        return null;
    }

    private GeocodeResult searchNominatim(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create("https://nominatim.openstreetmap.org/search?q="
                    + encoded + "&format=jsonv2&addressdetails=1&limit=1");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "FitOuts/1.0 (site-visit-geocode)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BadRequestException("Location search failed");
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                throw new BadRequestException("No matching location found");
            }
            return fromNominatimNode(root.get(0), query);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Location search failed: " + ex.getMessage());
        }
    }

    private GeocodeResult reverseNominatim(double lat, double lng) {
        try {
            URI uri = URI.create("https://nominatim.openstreetmap.org/reverse?lat="
                    + lat + "&lon=" + lng + "&format=jsonv2&addressdetails=1");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "FitOuts/1.0 (site-visit-geocode)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return coordsOnly(lat, lng, lat + ", " + lng);
            }
            JsonNode node = objectMapper.readTree(response.body());
            return fromNominatimNode(node, node.path("display_name").asText(null));
        } catch (Exception ex) {
            return coordsOnly(lat, lng, lat + ", " + lng);
        }
    }

    private GeocodeResult fromNominatimNode(JsonNode node, String fallbackName) {
        double lat = node.path("lat").asDouble();
        double lng = node.path("lon").asDouble();
        JsonNode address = node.path("address");
        String road = text(address, "road", "pedestrian", "footway", "residential");
        String house = text(address, "house_number");
        String suburb = text(address, "suburb", "neighbourhood", "quarter", "hamlet");
        String city = text(address, "city", "town", "village", "municipality", "county");
        String state = text(address, "state", "region");
        String country = text(address, "country");
        String postcode = text(address, "postcode");

        String line1 = StringUtils.hasText(house) && StringUtils.hasText(road)
                ? house + " " + road
                : (StringUtils.hasText(road) ? road : fallbackName);

        return GeocodeResult.builder()
                .latitude(toDecimal(lat))
                .longitude(toDecimal(lng))
                .displayName(node.path("display_name").asText(fallbackName))
                .addressLine1(line1)
                .addressLine2(suburb)
                .area(suburb)
                .city(StringUtils.hasText(city) ? city : "—")
                .state(StringUtils.hasText(state) ? state : "—")
                .country(StringUtils.hasText(country) ? country : "—")
                .pincode(StringUtils.hasText(postcode) ? postcode : "—")
                .build();
    }

    private GeocodeResult coordsOnly(double lat, double lng, String displayName) {
        return GeocodeResult.builder()
                .latitude(toDecimal(lat))
                .longitude(toDecimal(lng))
                .displayName(displayName)
                .addressLine1(displayName)
                .city("—")
                .state("—")
                .country("—")
                .pincode("—")
                .build();
    }

    private String buildGoogleShareUrl(double lat, double lng, String label) {
        String coords = lat + "," + lng;
        if (StringUtils.hasText(label)) {
            return "https://www.google.com/maps/search/?api=1&query="
                    + URLEncoder.encode(label, StandardCharsets.UTF_8);
        }
        return "https://www.google.com/maps?q=" + coords;
    }

    private String extractQueryParam(String url, String key) {
        int idx = url.indexOf('?');
        if (idx < 0) {
            return null;
        }
        String query = url.substring(idx + 1);
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return decode(kv[1]);
            }
        }
        return null;
    }

    private String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String text(JsonNode address, String... keys) {
        for (String key : keys) {
            String value = address.path(key).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }

    private double parseDouble(String value) {
        return Double.parseDouble(value);
    }

    private record Coordinates(double lat, double lng) {}
}
