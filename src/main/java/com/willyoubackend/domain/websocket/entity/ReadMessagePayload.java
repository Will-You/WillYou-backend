package com.willyoubackend.domain.websocket.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReadMessagePayload {
    private Long messageId; // 읽은 메시지의 ID
    private Long chatRoomId; // 해당 메시지의 채팅방 ID
}
