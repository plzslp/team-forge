package com.example.teamforge.gameprofile.dto;

import java.util.UUID;

/** 컬렉션을 조인하지 않고 페이지 대상과 참여자 이름을 먼저 조회하는 내부 결과다. */
public record GameProfileListRow(UUID profileId, String name) {}
