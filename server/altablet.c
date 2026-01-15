#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <linux/uinput.h>

#include "adb_bridge.h"

void emit(int fd, int type, int code, int val) {
    struct input_event ie = {0};
    ie.type = type;
    ie.code = code;
    ie.value = val;
    write(fd, &ie, sizeof(ie));
}

int main(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("open /dev/uinput"); return 1; }

    // --- SETUP: ABSOLUTE MOUSE MODE ---
    ioctl(fd, UI_SET_EVBIT, EV_SYN); 
    ioctl(fd, UI_SET_EVBIT, EV_KEY); 
    
    // CHANGE 1: Enable Standard Mouse Buttons instead of Pen/Touch
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);  // Left Click
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT); // Right Click (Optional, for side button)
    
    // Note: BTN_TOOL_PEN and BTN_TOUCH are REMOVED so the OS sees a "Mouse"

    ioctl(fd, UI_SET_EVBIT, EV_ABS); 

    // X Axis Configuration 
    // IMPORTANT: Ensure current_pen.x maps to 0-32767
    struct uinput_abs_setup abs_x = {
        .code = ABS_X,
        .absinfo = { .minimum = 0, .maximum = 32767, .resolution = 40 } 
    };
    ioctl(fd, UI_ABS_SETUP, &abs_x);

    // Y Axis Configuration
    struct uinput_abs_setup abs_y = {
        .code = ABS_Y,
        .absinfo = { .minimum = 0, .maximum = 32767, .resolution = 40 }
    };
    ioctl(fd, UI_ABS_SETUP, &abs_y);

    // CHANGE 2: Removed Pressure Setup (Mice don't have pressure)

    struct uinput_setup usetup = {0};
    strcpy(usetup.name, "Altablet Mouse Mode"); // Renamed for clarity
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor  = 0x1234;
    usetup.id.product = 0x5678;

    ioctl(fd, UI_DEV_SETUP, &usetup);
    ioctl(fd, UI_DEV_CREATE);

    printf("Device created as Absolute Mouse.\n");

    // Optional: Test Sequence (Moves cursor diagonally)
    for(int i=0; i<5000; i+=500) {
        emit(fd, EV_ABS, ABS_X, i);
        emit(fd, EV_ABS, ABS_Y, i);
        emit(fd, EV_SYN, SYN_REPORT, 0);
        usleep(2000);
    }

    int pen_socket = adb_bridge_init("127.0.0.1", 6789);
    
    if (pen_socket < 0) {
        printf("Failed to connect. Is the Android app running and ADB forwarded?\n");
        return 1;
    }

    printf("Bridge Connected! Emulating Mouse...\n");

    PenData current_pen;
    while (1) {
        if (adb_bridge_receive(pen_socket, &current_pen)) {
            
            // CHANGE 3: Logic for Absolute Mouse
            // We do NOT send BTN_TOOL_PEN.

            // 1. Send Coordinates
            emit(fd, EV_ABS, ABS_X, (int)current_pen.x);
            emit(fd, EV_ABS, ABS_Y, (int)current_pen.y);
            // Pressure is removed.

            // 2. Handle Clicking
            // If NOT hovering (Touching surface) -> Left Click Down
            // If Hovering -> Left Click Up
            //
            
            if (current_pen.pressure>0) {
                emit(fd, EV_KEY, BTN_LEFT, 1); 
            } else {
                emit(fd, EV_KEY, BTN_LEFT, 0); 
            }

            // 3. Sync
            emit(fd, EV_SYN, SYN_REPORT, 0);

        } else {
            printf("Connection lost.\n");
            // Safety release
            emit(fd, EV_KEY, BTN_LEFT, 0);
            emit(fd, EV_SYN, SYN_REPORT, 0);
            break;
        }
    }

    adb_bridge_close(pen_socket);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
