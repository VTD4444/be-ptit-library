package vn.edu.ptit.library.circulation.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edu.ptit.library.circulation.service.CirculationService;

/**
 * Dinh ky huy cac luot dat truoc da duoc giu sach nhung ban doc khong den nhan dung han,
 * de ban sach duoc nhuong cho nguoi ke tiep hoac tro lai ke.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final CirculationService circulationService;

    // Mac dinh moi 30 phut; doi bang thuoc tinh library.holds.expiry-cron
    @Scheduled(cron = "${library.holds.expiry-cron:0 */30 * * * *}")
    public void expireOverduePickups() {
        try {
            int count = circulationService.expireOverduePickups();
            if (count > 0) {
                log.info("Da huy {} luot dat truoc qua han nhan sach", count);
            }
        } catch (RuntimeException e) {
            // Loi tam thoi (mat ket noi CSDL...) khong duoc lam dung scheduler; lan chay sau se thu lai
            log.warn("Khong the xu ly dat truoc qua han nhan sach: {}", e.getMessage());
        }
    }
}
