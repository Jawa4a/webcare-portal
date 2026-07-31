package com.webcare.portal.website;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Service
public class UrlPolicyService {

    private static final Set<String> BLOCKED_SUFFIXES = Set.of(".localhost", ".local", ".internal", ".home", ".lan");
    private final HostResolver hostResolver;
    private final boolean allowPrivateAddresses;

    public UrlPolicyService(HostResolver hostResolver) {
        this(hostResolver, false);
    }

    @Autowired
    public UrlPolicyService(
            HostResolver hostResolver,
            @Value("${webcare.audit.allow-private-addresses:false}") boolean allowPrivateAddresses) {
        this.hostResolver = hostResolver;
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    public ValidatedUrl validateAndNormalize(String input) {
        ParsedTarget parsed = parse(input);
        List<InetAddress> addresses;
        try {
            addresses = hostResolver.resolve(parsed.host());
        } catch (UnknownHostException exception) {
            throw rejected("URL_HOST_UNRESOLVED", "Домен не удалось разрешить через DNS.");
        }
        if (addresses.isEmpty()
                || !allowPrivateAddresses && addresses.stream().anyMatch(this::isBlockedAddress)) {
            throw rejected("PRIVATE_ADDRESS", "Локальные и служебные сетевые адреса запрещены.");
        }
        return new ValidatedUrl(parsed.uri().toASCIIString(), parsed.host());
    }

    public boolean isSameDomain(String rootHost, String candidateUrl) {
        ParsedTarget candidate = parse(candidateUrl);
        return rootHost.equalsIgnoreCase(candidate.host());
    }

    public Optional<String> normalizeCrawlLink(String rootHost, String baseUrl, String href) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }
        try {
            URI resolved = new URI(baseUrl).resolve(href.trim());
            URI withoutFragment = new URI(
                    resolved.getScheme(),
                    resolved.getRawAuthority(),
                    resolved.getRawPath(),
                    resolved.getRawQuery(),
                    null);
            ParsedTarget candidate = parse(withoutFragment.toASCIIString());
            return rootHost.equalsIgnoreCase(candidate.host())
                    ? Optional.of(candidate.uri().toASCIIString())
                    : Optional.empty();
        } catch (URISyntaxException | UrlNotAllowedException exception) {
            return Optional.empty();
        }
    }

    private ParsedTarget parse(String input) {
        if (input == null || input.isBlank()) {
            throw rejected("URL_REQUIRED", "Укажите URL сайта.");
        }

        URI source;
        try {
            source = new URI(input.trim());
        } catch (URISyntaxException exception) {
            throw rejected("URL_INVALID", "URL имеет неверный формат.");
        }

        String scheme = source.getScheme() == null ? "" : source.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw rejected("URL_SCHEME_NOT_ALLOWED", "Разрешены только HTTP и HTTPS URL.");
        }
        if (source.isOpaque() || source.getRawAuthority() == null) {
            throw rejected("URL_INVALID", "URL должен содержать доменное имя.");
        }
        if (source.getRawUserInfo() != null || source.getRawAuthority().contains("@")) {
            throw rejected("URL_CREDENTIALS_NOT_ALLOWED", "Логин и пароль в URL запрещены.");
        }
        if (source.getRawFragment() != null) {
            throw rejected("URL_FRAGMENT_NOT_ALLOWED", "Удалите фрагмент после символа #.");
        }

        HostAndPort hostAndPort = extractHostAndPort(source);
        String host = normalizeHost(hostAndPort.host());
        int port = hostAndPort.port();
        if (port != -1 && port != 80 && port != 443) {
            throw rejected("URL_PORT_NOT_ALLOWED", "Разрешены только стандартные HTTP и HTTPS порты.");
        }
        if (host.equals("localhost") || BLOCKED_SUFFIXES.stream().anyMatch(host::endsWith)) {
            throw rejected("PRIVATE_ADDRESS", "Локальные и служебные домены запрещены.");
        }

        int normalizedPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)
                ? -1
                : port;
        String path = source.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }

        try {
            URI normalized = new URI(
                    scheme,
                    null,
                    host,
                    normalizedPort,
                    path,
                    source.getRawQuery(),
                    null).normalize();
            return new ParsedTarget(normalized, host);
        } catch (URISyntaxException exception) {
            throw rejected("URL_INVALID", "URL не удалось нормализовать.");
        }
    }

    private HostAndPort extractHostAndPort(URI source) {
        if (source.getHost() != null) {
            return new HostAndPort(source.getHost(), source.getPort());
        }

        String authority = source.getRawAuthority();
        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            if (closingBracket < 0) {
                throw rejected("URL_INVALID", "IPv6-адрес имеет неверный формат.");
            }
            String host = authority.substring(1, closingBracket);
            String portPart = authority.substring(closingBracket + 1);
            int port = portPart.isEmpty() ? -1 : parsePort(portPart);
            return new HostAndPort(host, port);
        }

        int separator = authority.lastIndexOf(':');
        if (separator > 0 && authority.indexOf(':') == separator) {
            return new HostAndPort(authority.substring(0, separator), parsePort(authority.substring(separator)));
        }
        return new HostAndPort(authority, -1);
    }

    private int parsePort(String value) {
        if (!value.startsWith(":")) {
            throw rejected("URL_INVALID", "Порт в URL имеет неверный формат.");
        }
        try {
            int port = Integer.parseInt(value.substring(1));
            if (port < 1 || port > 65535) {
                throw rejected("URL_INVALID", "Порт в URL вне допустимого диапазона.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw rejected("URL_INVALID", "Порт в URL имеет неверный формат.");
        }
    }

    private String normalizeHost(String rawHost) {
        String host = rawHost.endsWith(".") ? rawHost.substring(0, rawHost.length() - 1) : rawHost;
        if (host.isBlank()) {
            throw rejected("URL_INVALID", "URL должен содержать доменное имя.");
        }
        if (host.contains(":")) {
            return host.toLowerCase(Locale.ROOT);
        }
        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw rejected("URL_INVALID", "Доменное имя имеет неверный формат.");
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isBlockedIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            int first = unsigned(bytes[0]);
            int second = unsigned(bytes[1]);
            if ((first & 0xfe) == 0xfc || (first == 0xfe && (second & 0xc0) == 0x80)) {
                return true;
            }
            if (first == 0x20 && second == 0x01 && unsigned(bytes[2]) == 0x0d && unsigned(bytes[3]) == 0xb8) {
                return true;
            }
            if (isIpv4Mapped(bytes)) {
                return isBlockedIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
            }
            if (first == 0x20 && second == 0x02) {
                return isBlockedIpv4(new byte[]{bytes[2], bytes[3], bytes[4], bytes[5]});
            }
        }
        return false;
    }

    private boolean isBlockedIpv4(byte[] bytes) {
        int a = unsigned(bytes[0]);
        int b = unsigned(bytes[1]);
        int c = unsigned(bytes[2]);
        return a == 0
                || a == 10
                || a == 100 && b >= 64 && b <= 127
                || a == 127
                || a == 169 && b == 254
                || a == 172 && b >= 16 && b <= 31
                || a == 192 && b == 0 && (c == 0 || c == 2)
                || a == 192 && b == 168
                || a == 198 && (b == 18 || b == 19)
                || a == 198 && b == 51 && c == 100
                || a == 203 && b == 0 && c == 113
                || a >= 224;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return unsigned(bytes[10]) == 0xff && unsigned(bytes[11]) == 0xff;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private UrlNotAllowedException rejected(String code, String message) {
        return new UrlNotAllowedException(code, message);
    }

    private record ParsedTarget(URI uri, String host) {
    }

    private record HostAndPort(String host, int port) {
    }

    public record ValidatedUrl(String url, String host) {
    }
}
