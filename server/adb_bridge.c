#ifdef _WIN32
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0600
#endif
#endif

#include "adb_bridge.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* =========================================================
 *  PLATFORM SOCKET INCLUDES & MACROS
 * ========================================================= */
#ifdef __linux__
    #include <unistd.h>
    #include <arpa/inet.h>
    #include <sys/socket.h>
    #include <netinet/tcp.h>
#endif
#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
#endif

/* =========================================================
 *  Line buffer for reassembling TCP stream into messages.
 *  TCP can deliver partial lines or multiple lines in one
 *  recv(), so we accumulate data and extract complete lines.
 * ========================================================= */
static char line_buf[4096];
static int  line_len = 0;

/* =========================================================
 *  adb_bridge_init
 * ========================================================= */
intptr_t adb_bridge_init(const char *host, int port)
{
#ifdef _WIN32
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        return -1;
    }
#endif

    int sock = (int)socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return -1;

    /* Disable Nagle's algorithm — send data immediately without buffering */
    int flag = 1;
    setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, (char*)&flag, sizeof(flag));

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    inet_pton(AF_INET, host, &server_addr.sin_addr);

    if (connect(sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0)
    {
        return -1;
    }
    
    /* Reset line buffer on new connection */
    line_len = 0;

    return sock;
}

/* =========================================================
 *  adb_bridge_receive
 *  
 *  Properly handles TCP stream reassembly:
 *  - Accumulates data in a line buffer
 *  - Extracts and parses one complete '\n'-terminated line
 *  - Preserves leftover data for the next call
 *  
 *  Returns:  1 = valid data parsed
 *            0 = no complete line yet (or parse error)
 *           -1 = connection closed / error
 * ========================================================= */
int adb_bridge_receive(int sock, PenData *out_data)
{
    size_t pos = 0;

    while (pos < line_len) {
        unsigned char type = (unsigned char)line_buf[pos];

        if (type == 0) {          /* keepalive */
            pos++;
            continue;
        }

        if (type == 1) {
            if (line_len - pos < 10) break;   /* need more bytes */

            uint16_t x_be, y_be;
            uint32_t p_be;
            memcpy(&x_be, line_buf + pos + 1, 2);
            memcpy(&y_be, line_buf + pos + 3, 2);
            memcpy(&p_be, line_buf + pos + 5, 4);

            out_data->x = (float)ntohs(x_be);
            out_data->y = (float)ntohs(y_be);

            uint32_t p_host = ntohl(p_be);
            float pressure;
            memcpy(&pressure, &p_host, 4);
            out_data->pressure = isfinite(pressure) ? pressure : 0.0f;

            out_data->is_hovering = (line_buf[pos + 9] != 0);

            pos += 10;
            line_len -= pos;
            if (line_len > 0) memmove(line_buf, line_buf + pos, line_len);
            return 1;
        }

        /* unknown type: just advance the cursor, don't shift memory yet */
        pos++;
    }

    /* drained keepalives/garbage without a full packet — compact once */
    if (pos > 0) {
        line_len -= pos;
        if (line_len > 0) memmove(line_buf, line_buf + pos, line_len);
    }

    int space = (int)sizeof(line_buf) - (int)line_len;
    if (space <= 0) {
        line_len = 0;
        return 0;
    }

    int bytes_read = recv(sock, line_buf + line_len, space, 0);
    if (bytes_read <= 0) {
        printf("Connection closed by Android device.\n");
        return -1;
    }
    line_len += bytes_read;
    return 0;
}

/* =========================================================
 *  adb_bridge_close
 * ========================================================= */
void adb_bridge_close(int sock)
{
    if (sock >= 0) {
#ifdef __linux__
        close(sock);
#endif
#ifdef _WIN32
        closesocket(sock);
        WSACleanup();
#endif
    }
    /* Reset line buffer */
    line_len = 0;
}
