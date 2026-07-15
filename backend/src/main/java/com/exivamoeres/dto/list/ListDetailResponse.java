package com.exivamoeres.dto.list;

import com.exivamoeres.domain.HuntingList;

import java.util.List;

public record ListDetailResponse(
        ListSummaryResponse summary,
        Long ownerId,
        List<MembershipResponse> members
) {
    public static ListDetailResponse from(HuntingList list, long memberCount, int maxMembers,
                                          List<MembershipResponse> members) {
        return new ListDetailResponse(
                ListSummaryResponse.from(list, memberCount, maxMembers),
                list.getOwner().getId(),
                members);
    }
}
