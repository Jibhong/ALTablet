#ifdef _WIN32
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0600
#endif
#endif

#include "adb_bridge.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

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
    /* Try to find a complete line in the existing buffer first */
    char *newline = (char *)memchr(line_buf, '\n', line_len);

    if (!newline)
    {
        /* Need more data from the network */
        int space = (int)sizeof(line_buf) - line_len - 1;
        if (space <= 0)
        {
            /* Buffer full with no newline — discard and reset */
            line_len = 0;
            return 0;
        }

        int bytes_read = recv(sock, line_buf + line_len, space, 0);
        if (bytes_read <= 0)
        {
            printf("Connection closed by Android device.\n");
            return -1;
        }

        line_len += bytes_read;
        newline = (char *)memchr(line_buf, '\n', line_len);

        if (!newline)
            return 0; /* Still no complete line — wait for more */
    }

    /* Null-terminate at the newline so sscanf stops there */
    *newline = '\0';

    /* Skip keepalive null bytes — find start of actual data */
    char *start = line_buf;
    while (start < newline && *start == '\0')
        start++;

    /* Default is_hovering in case sscanf only parses 3 fields */
    out_data->is_hovering = 0;

    int matched = sscanf(start, "%f,%f,%f,%d",
                         &out_data->x, &out_data->y,
                         &out_data->pressure, &out_data->is_hovering);

    /* Shift remaining data to the front of the buffer */
    int consumed = (int)(newline - line_buf) + 1;
    line_len -= consumed;
    if (line_len > 0)
        memmove(line_buf, newline + 1, line_len);

    return (matched >= 3) ? 1 : 0;
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
