package gb.j2.chat.library;

public class Messages {
    public static final String DELIMITER = "§";
    public static final String AUTH_REQUEST = "/auth_request";
    public static final String AUTH_ACCEPT = "/auth_accept";
    public static final String AUTH_ERROR = "/auth_error";
    public static final String BROADCAST = "/bcast";
    public static final String MESSAGE_FORMAT_ERROR = "/msg_fmt_err";
    public static final String USER_LIST = "/user_list";
    public static final String CLIENT_BCAST = "/cl_bcast";
    public static final String RENAME = "/renameto";

    public static String getAuthRequest(String login, String password) {
        return AUTH_REQUEST + DELIMITER + login + DELIMITER + password;
    }

    public static String getAuthAccept(String nickname) {
        return AUTH_ACCEPT + DELIMITER + nickname;
    }

    public static String getAuthError() {
        return AUTH_ERROR;
    }

    public static String getBroadcast(String src, String msg) {
        return BROADCAST + DELIMITER + System.currentTimeMillis() +
                DELIMITER + src + DELIMITER + msg;
    }

    public static String getMessageFormatError(String msg) {
        return MESSAGE_FORMAT_ERROR + DELIMITER + msg;
    }

    public static String getUserList(String list) {
        return USER_LIST + DELIMITER + list;
    }

    public static String getClientBcast(String msg) {
        return CLIENT_BCAST + DELIMITER + msg;
    }

}
