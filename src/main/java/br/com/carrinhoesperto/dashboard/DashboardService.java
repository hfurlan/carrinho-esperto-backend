package br.com.carrinhoesperto.dashboard;

import br.com.carrinhoesperto.dashboard.dto.DashboardSpendingResponse;
import br.com.carrinhoesperto.dashboard.dto.DashboardSummaryResponse;
import br.com.carrinhoesperto.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only aggregation for the consumer dashboard (M006 §4, §8). Owner-scoped
 * via {@link UserService#currentUserId()} — never a client-supplied id.
 *
 * <p>This is a thin read layer over Fiscal + Resolution, deliberately <em>not</em>
 * a new Analytics bounded context and not a generic analytics framework.
 */
@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final UserService userService;

    DashboardService(DashboardRepository dashboardRepository, UserService userService) {
        this.dashboardRepository = dashboardRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        return dashboardRepository.summary(currentUser());
    }

    @Transactional(readOnly = true)
    public DashboardSpendingResponse spending() {
        UUID ownerId = currentUser();
        return new DashboardSpendingResponse(
            dashboardRepository.spendingByMonth(ownerId),
            dashboardRepository.spendingByIssuer(ownerId));
    }

    private UUID currentUser() {
        return userService.currentUserId();
    }
}
