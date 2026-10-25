package com.nhom15.drawguess.common.protocol;

public enum MessageType {

    // Test
    PING,
    PONG,

    // Register
    REGISTER,
    REGISTER_SUCCESS,
    REGISTER_FAILED,

    // Authentication
    LOGIN,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    UPDATE_AVATAR,
    AVATAR_UPDATED,
    AVATAR_FAILED,
    ACTION_SUCCESS,
    ACTION_FAILED,

    // Rooms
    GET_ROOMS,
    ROOM_LIST,
    JOIN_ROOM,
    JOIN_SUCCESS,
    JOIN_FAILED,
    LEAVE_ROOM,
    ROOM_INFO,
    READY,
    CANCEL_READY,
    READY_STATUS,
    GAME_STARTING,
    START_GAME,

    ROUND_CATEGORY,
    DRAW_WORD,
    SUBMIT_DRAW,
    DRAW_UPLOAD,
    ROUND_START,
    GUESS,
    GUESS_RESULT,
    SCORE_UPDATE,
    TIME_UPDATE,
    TIME_UP,
    ANSWER,
    GAME_END,
    GAME_RESULT,
    GET_HISTORY, HISTORY_LIST, GET_HISTORY_DETAIL, HISTORY_DETAIL, HISTORY_FAILED,
    GET_HISTORY_IMAGE, HISTORY_IMAGE, HISTORY_IMAGE_FAILED,
    GET_CATALOG, CATALOG, EDIT_CATALOG, CATALOG_SAVED, CATALOG_FAILED,
    GAME_ERROR,
    HINT,
    KICK_PLAYER, KICKED,
    // Số tài khoản đang đăng nhập trên server, gửi kèm ROOM_LIST.
    ONLINE_COUNT,
    // Báo cáo tranh trong phiên đoán: quá nửa người đoán báo cáo thì người vẽ mất điểm vòng đó.
    REPORT_DRAWING, REPORT_STATUS,
    // Danh sách người đang online và lời mời vào phòng.
    GET_ONLINE_USERS, ONLINE_USERS, INVITE_PLAYER, INVITATION, DECLINE_INVITE, INVITE_DECLINED,
    // Mã nối lại phiên: máy vừa mất mạng dùng mã này để lấy lại phiên cũ còn treo trên server.
    GET_RESUME_TOKEN, RESUME_TOKEN,
    // Mọi người sẵn sàng: đếm ngược trước khi vào trận (data: số giây); ai bấm "chưa sẵn sàng" thì hủy (data: tên người đó).
    START_COUNTDOWN, START_CANCELLED,
    // Chat trong phòng: client gửi chữ, server phát ChatMessage cho cả phòng.
    CHAT_SEND, CHAT_MESSAGE
}
