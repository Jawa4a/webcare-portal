package com.webcare.portal.website;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.List;

import org.junit.jupiter.api.Test;

class UrlPolicyServiceTest {

    @Test
    void normalizesPublicHttpsUrl() throws Exception {
        UrlPolicyService service = serviceWith("93.184.216.34");

        var result = service.validateAndNormalize("HTTPS://Example.COM:443/a/../status?full=1");

        assertThat(result.url()).isEqualTo("https://example.com/status?full=1");
        assertThat(result.host()).isEqualTo("example.com");
    }

    @Test
    void rejectsPrivateDnsResult() throws Exception {
        UrlPolicyService service = serviceWith("10.10.0.8");

        assertThatThrownBy(() -> service.validateAndNormalize("https://example.com"))
                .isInstanceOf(UrlNotAllowedException.class)
                .extracting("code")
                .isEqualTo("PRIVATE_ADDRESS");
    }

    @Test
    void allowsPrivateDnsOnlyWhenExplicitTestModeIsEnabled() throws Exception {
        InetAddress privateAddress = InetAddress.getByName("10.10.0.8");
        UrlPolicyService service = new UrlPolicyService(host -> List.of(privateAddress), true);

        assertThat(service.validateAndNormalize("http://test-site/issues/").host())
                .isEqualTo("test-site");
    }

    @Test
    void rejectsHostWhenAnyDnsAnswerIsPrivate() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        InetAddress privateAddress = InetAddress.getByName("192.168.1.10");
        UrlPolicyService service = new UrlPolicyService(host -> List.of(publicAddress, privateAddress));

        assertThatThrownBy(() -> service.validateAndNormalize("https://example.com"))
                .isInstanceOf(UrlNotAllowedException.class)
                .extracting("code")
                .isEqualTo("PRIVATE_ADDRESS");
    }

    @Test
    void rejectsPrivateIpv6Result() throws Exception {
        UrlPolicyService service = serviceWith("fc00::1");

        assertThatThrownBy(() -> service.validateAndNormalize("https://example.com"))
                .isInstanceOf(UrlNotAllowedException.class)
                .extracting("code")
                .isEqualTo("PRIVATE_ADDRESS");
    }

    @Test
    void rejectsLocalhostBeforeDnsLookup() {
        UrlPolicyService service = new UrlPolicyService(host -> {
            throw new AssertionError("DNS lookup must not run");
        });

        assertThatThrownBy(() -> service.validateAndNormalize("http://localhost/admin"))
                .isInstanceOf(UrlNotAllowedException.class)
                .extracting("code")
                .isEqualTo("PRIVATE_ADDRESS");
    }

    @Test
    void rejectsCredentialsAndNonStandardPorts() throws Exception {
        UrlPolicyService service = serviceWith("93.184.216.34");

        assertThatThrownBy(() -> service.validateAndNormalize("https://user:pass@example.com"))
                .isInstanceOf(UrlNotAllowedException.class)
                .extracting("code")
                .isEqualTo("URL_CREDENTIALS_NOT_ALLOWED");
        assertThatThrownBy(() -> service.validateAndNormalize("https://example.com:8443"))
                .isInstanceOf(UrlNotAllowedException.class)
                .extracting("code")
                .isEqualTo("URL_PORT_NOT_ALLOWED");
    }

    @Test
    void crawlScopeRequiresExactHost() throws Exception {
        UrlPolicyService service = serviceWith("93.184.216.34");

        assertThat(service.isSameDomain("example.com", "https://example.com/about")).isTrue();
        assertThat(service.isSameDomain("example.com", "https://cdn.example.com/image")).isFalse();
        assertThat(service.isSameDomain("example.com", "https://example.org/")).isFalse();
    }

    @Test
    void normalizesRelativeCrawlLinksAndRemovesFragments() throws Exception {
        UrlPolicyService service = serviceWith("93.184.216.34");

        assertThat(service.normalizeCrawlLink(
                "example.com", "https://example.com/catalog/page/", "../about#team"))
                .contains("https://example.com/catalog/about");
        assertThat(service.normalizeCrawlLink(
                "example.com", "https://example.com/", "https://other.example/path"))
                .isEmpty();
        assertThat(service.normalizeCrawlLink(
                "example.com", "https://example.com/", "javascript:alert(1)"))
                .isEmpty();
    }

    private UrlPolicyService serviceWith(String address) throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        return new UrlPolicyService(host -> List.of(resolved));
    }
}
