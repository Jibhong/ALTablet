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

    ioctl(fd, UI_SET_EVBIT, EV_SYN); //this pen device support synchronize events
    ioctl(fd, UI_SET_EVBIT, EV_KEY); //pen has button
    ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH); //pen can touching
    ioctl(fd, UI_SET_KEYBIT, BTN_TOOL_PEN); //pen can hover
    ioctl(fd, UI_SET_KEYBIT, BTN_STYLUS); //pen has side button

    ioctl(fd, UI_SET_EVBIT, EV_ABS); //pen is absolute coordinate

    // X Axis Configuration (40 units/mm resolution)
    struct uinput_abs_setup abs_x = {
        .code = ABS_X,
        .absinfo = { .minimum = 0, .maximum = 32767, .resolution = 40 } 
    };
    ioctl(fd, UI_ABS_SETUP, &abs_x);

    // Y Axis Configuration (40 units/mm resolution)
    struct uinput_abs_setup abs_y = {
        .code = ABS_Y,
        .absinfo = { .minimum = 0, .maximum = 32767, .resolution = 40 }
    };
    ioctl(fd, UI_ABS_SETUP, &abs_y);

    // Pressure Configuration (Resolution 0 is fine for pressure)
    struct uinput_abs_setup abs_p = {
        .code = ABS_PRESSURE,
        .absinfo = { .minimum = 0, .maximum = 1024, .resolution = 0 }
    };
    ioctl(fd, UI_ABS_SETUP, &abs_p);

    struct uinput_setup usetup = {0};
    strcpy(usetup.name, "Altablet Fixed Device");
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor  = 0x1234;
    usetup.id.product = 0x5678;

    ioctl(fd, UI_DEV_SETUP, &usetup);
    ioctl(fd, UI_DEV_CREATE);

    printf("Device created. Check 'libinput list-devices' or 'evtest' now.\n");
    printf("Press ENTER to draw a test line and exit...\n");
//     getchar();

    // Sequence: Proximity -> Touch -> Move -> Untouch -> Out of Proximity
    emit(fd, EV_KEY, BTN_TOOL_PEN, 1);
    emit(fd, EV_SYN, SYN_REPORT, 0);
    usleep(50000);

    emit(fd, EV_KEY, BTN_TOUCH, 1);
    for(int i=0; i<32767; i+=1000) {
        emit(fd, EV_ABS, ABS_X, i);
        emit(fd, EV_ABS, ABS_Y, i);
        emit(fd, EV_ABS, ABS_PRESSURE, 500);
        emit(fd, EV_SYN, SYN_REPORT, 0);
        usleep(2000);
    }

    emit(fd, EV_KEY, BTN_TOUCH, 0);
    emit(fd, EV_KEY, BTN_TOOL_PEN, 0);
    emit(fd, EV_SYN, SYN_REPORT, 0);

//     sleep(1);
//     ioctl(fd, UI_DEV_DESTROY);
//     close(fd);

    int pen_socket = adb_bridge_init("127.0.0.1", 6789);
    
    if (pen_socket < 0) {
        printf("Failed to connect. Is the Android app running and ADB forwarded?\n");
        return 1;
    }

    printf("Bridge Connected! Listening for Pen data...\n");

    PenData current_pen;
    while (1) {
        if (adb_bridge_receive(pen_socket, &current_pen)) {
             printf("X: %.2f | Y: %.2f | Pressure: %.2f | Hover: %s\n",
                   current_pen.x, 
                   current_pen.y, 
                   current_pen.pressure,
                   current_pen.is_hovering ? "YES" : "NO");
            /*if (!current_pen.is_hovering){
                emit(fd, EV_KEY, BTN_TOOL_PEN, 1);
                emit(fd, EV_SYN, SYN_REPORT, 0);
            }*/
            emit(fd, EV_ABS, ABS_X, current_pen.x);
            emit(fd, EV_ABS, ABS_Y, current_pen.y);
            emit(fd, EV_ABS, ABS_PRESSURE, 500);
            emit(fd, EV_SYN, SYN_REPORT, 0);

            //emit(fd, EV_KEY, BTN_TOOL_PEN, 0);
            //emit(fd, EV_SYN, SYN_REPORT, 0);
        } else {
            printf("Connection lost.\n");
            break;
        }
    }

    adb_bridge_close(pen_socket);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
