package br.com.carrinhoesperto.dashboard;

import br.com.carrinhoesperto.dashboard.dto.DashboardSpendingResponse;
import br.com.carrinhoesperto.dashboard.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consumer dashboard aggregates (M006). Authenticated; every figure is scoped to
 * the caller's own imported receipts.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {
        return dashboardService.summary();
    }

    @GetMapping("/spending")
    public DashboardSpendingResponse spending() {
        return dashboardService.spending();
    }
}
