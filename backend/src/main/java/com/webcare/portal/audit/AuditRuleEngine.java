package com.webcare.portal.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Service
public class AuditRuleEngine {

    private static final int SLOW_RESPONSE_MS = 2_000;
    private static final int TLS_WARNING_DAYS = 30;

    public List<FindingDraft> evaluate(PageCheckInput input) {
        List<FindingDraft> findings = new ArrayList<>();
        if (input.errorCode() != null) {
            findings.add(new FindingDraft(
                    "FETCH_FAILED",
                    FindingSeverity.HIGH,
                    "Страница недоступна для проверки",
                    input.errorMessage() == null ? "Не удалось получить страницу." : input.errorMessage(),
                    "Проверьте доступность страницы, DNS, TLS и настройки сервера.",
                    input.url()));
        }

        if (input.httpStatus() != null && input.httpStatus() >= 400) {
            FindingSeverity severity = input.httpStatus() >= 500
                    ? FindingSeverity.CRITICAL
                    : FindingSeverity.HIGH;
            findings.add(new FindingDraft(
                    "HTTP_STATUS_ERROR",
                    severity,
                    "Ошибочный HTTP-статус",
                    "Страница вернула HTTP " + input.httpStatus() + ".",
                    "Исправьте адрес, маршрутизацию или обработку ошибки на сервере.",
                    String.valueOf(input.httpStatus())));
        }

        if (input.responseTimeMs() != null && input.responseTimeMs() > SLOW_RESPONSE_MS) {
            findings.add(new FindingDraft(
                    "SLOW_RESPONSE",
                    FindingSeverity.MEDIUM,
                    "Медленный ответ страницы",
                    "Ответ получен за " + input.responseTimeMs() + " мс.",
                    "Сократите серверное время ответа и проверьте внешние зависимости.",
                    input.responseTimeMs() + "ms"));
        }

        if (input.redirectCount() > 0) {
            findings.add(new FindingDraft(
                    "REDIRECT_CHAIN",
                    input.redirectCount() >= 3 ? FindingSeverity.MEDIUM : FindingSeverity.LOW,
                    "Страница использует перенаправление",
                    "До конечного ответа выполнено перенаправлений: " + input.redirectCount() + ".",
                    "Ссылайтесь сразу на конечный URL и сократите цепочку редиректов.",
                    String.valueOf(input.redirectCount())));
        }

        appendTlsFinding(input.certificateExpiresAt(), findings);
        if (input.document() != null) {
            appendHtmlFindings(input.document(), findings);
        }
        return List.copyOf(findings);
    }

    private void appendTlsFinding(Instant expiresAt, List<FindingDraft> findings) {
        if (expiresAt == null) {
            return;
        }
        long days = Duration.between(Instant.now(), expiresAt).toDays();
        if (days < 0) {
            findings.add(new FindingDraft(
                    "TLS_EXPIRED", FindingSeverity.CRITICAL, "TLS-сертификат истёк",
                    "Срок действия сертификата уже завершился.",
                    "Немедленно выпустите и установите новый TLS-сертификат.", expiresAt.toString()));
        } else if (days < TLS_WARNING_DAYS) {
            findings.add(new FindingDraft(
                    "TLS_EXPIRES_SOON", FindingSeverity.HIGH, "TLS-сертификат скоро истекает",
                    "До окончания срока действия осталось менее 30 дней.",
                    "Продлите сертификат и проверьте автоматическое обновление.", expiresAt.toString()));
        }
    }

    private void appendHtmlFindings(Document document, List<FindingDraft> findings) {
        if (document.title().isBlank()) {
            findings.add(new FindingDraft(
                    "TITLE_MISSING", FindingSeverity.HIGH, "Отсутствует title",
                    "У страницы нет непустого элемента title.",
                    "Добавьте уникальный и содержательный title.", "title"));
        }

        Element description = document.selectFirst("meta[name=description]");
        if (description == null || description.attr("content").isBlank()) {
            findings.add(new FindingDraft(
                    "META_DESCRIPTION_MISSING", FindingSeverity.MEDIUM, "Отсутствует meta description",
                    "У страницы нет непустого описания для поисковой выдачи.",
                    "Добавьте уникальный meta description.", "meta[name=description]"));
        }

        int h1Count = document.select("h1").size();
        if (h1Count == 0) {
            findings.add(new FindingDraft(
                    "H1_MISSING", FindingSeverity.HIGH, "Отсутствует H1",
                    "На странице не найден заголовок первого уровня.",
                    "Добавьте один основной H1, описывающий содержимое страницы.", "h1"));
        } else if (h1Count > 1) {
            findings.add(new FindingDraft(
                    "MULTIPLE_H1", FindingSeverity.MEDIUM, "Несколько H1",
                    "На странице найдено H1: " + h1Count + ".",
                    "Оставьте один основной H1 и понизьте уровень остальных заголовков.", String.valueOf(h1Count)));
        }

        Element canonical = document.selectFirst("link[rel~=canonical]");
        if (canonical == null || canonical.attr("href").isBlank()) {
            findings.add(new FindingDraft(
                    "CANONICAL_MISSING", FindingSeverity.LOW, "Не указан canonical",
                    "На странице нет канонического URL.",
                    "Добавьте link rel=canonical с абсолютным адресом основной версии страницы.", "link[rel=canonical]"));
        }

        boolean noindex = document.select("meta[name=robots], meta[name=googlebot]").stream()
                .map(element -> element.attr("content").toLowerCase(Locale.ROOT))
                .anyMatch(content -> content.contains("noindex"));
        if (noindex) {
            findings.add(new FindingDraft(
                    "NOINDEX", FindingSeverity.MEDIUM, "Индексация страницы запрещена",
                    "Директива robots содержит noindex.",
                    "Уберите noindex, если страница должна появляться в поиске.", "meta[name=robots]"));
        }

        long imagesWithoutAlt = document.select("img").stream()
                .filter(image -> !image.hasAttr("alt") || image.attr("alt").isBlank())
                .count();
        if (imagesWithoutAlt > 0) {
            findings.add(new FindingDraft(
                    "IMAGE_ALT_MISSING", FindingSeverity.MEDIUM, "У изображений отсутствует alt",
                    "Изображений без текстового описания: " + imagesWithoutAlt + ".",
                    "Добавьте осмысленный alt либо пустой alt для декоративных изображений.",
                    String.valueOf(imagesWithoutAlt)));
        }
    }
}
