package com.exivamoeres.service.impl;

import com.exivamoeres.config.TeamProperties;
import com.exivamoeres.domain.Plan;
import com.exivamoeres.service.PlanPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PlanPolicyImpl implements PlanPolicy {

    private final TeamProperties teamProperties;

    public PlanPolicyImpl(TeamProperties teamProperties) {
        this.teamProperties = teamProperties;
    }

    @Override
    public int maxActiveTeams(Plan plan) {
        // Premium é "ilimitado" — modelado como um teto altíssimo em vez de um
        // caso especial de negócio, o que mantém a checagem de limite uniforme.
        return plan == Plan.PREMIUM ? Integer.MAX_VALUE : teamProperties.freeActiveLimit();
    }

    @Override
    public Duration teamDuration(Plan plan) {
        return plan == Plan.PREMIUM ? teamProperties.premiumDuration() : teamProperties.freeDuration();
    }
}
