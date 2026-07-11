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
#endif
#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
#endif

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

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    inet_pton(AF_INET, host, &server_addr.sin_addr);

    if (connect(sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0)
    {
        return -1;
    }
    
    return sock;
}

/* =========================================================
 *  adb_bridge_receive
 * ========================================================= */
int adb_bridge_receive(int sock, PenData *out_data)
{
    char buffer[256];
    int bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
    
    if (bytes_read > 0)
    {
        buffer[bytes_read] = '\0';
        // printf("Received: %s len: %d\n", buffer, bytes_read);
    }

    
    else if (bytes_read <= 0)
    {
        printf("Connection closed by Android device.\n");
        return -1;
    }
    int matched = sscanf(buffer, "%f,%f,%f,%d", &out_data->x, &out_data->y, &out_data->pressure, &out_data->is_hovering);
    if(matched <= 0) return 0;
    
    return 1;
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
}
