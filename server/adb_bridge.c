#include "adb_bridge.h"

#include <stdio.h>

#include <stdlib.h>

#include <string.h>

#include <unistd.h>

#include <arpa/inet.h>

int adb_bridge_init(const char * host, int port) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return -1;

    struct sockaddr_in server_addr;
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    inet_pton(AF_INET, host, & server_addr.sin_addr);

    if (connect(sock, (struct sockaddr * ) & server_addr, sizeof(server_addr)) < 0) {
        close(sock);
        return -1;
    }
    return sock;

}

int adb_bridge_receive(int sock, PenData * out_data) {
    char buffer[256];
    int bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0);
    if (bytes_read > 0) {
        buffer[bytes_read] = '\0'; // Null-terminate to print as string
        printf("Received: %s", buffer);
    } else if (bytes_read == 0) {
        printf("Connection closed by Android device.\n");
        return -1;
    }
    //     buffer[bytes_read] = '\0';

    // Parse the format: "x,y,pressure,isHovering\n"
    // We use sscanf for easy string-to-float conversion
    int hover_val;
    int matched = sscanf(buffer, "%f,%f,%f,%d", &
        out_data -> x, &
        out_data -> y, &
        out_data -> pressure, &
        hover_val);

    out_data -> is_hovering = hover_val;

//     return (matched == 4);
    return 1;

}

void adb_bridge_close(int sock) {
    if (sock >= 0) close(sock);
}
