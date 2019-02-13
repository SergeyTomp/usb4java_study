import org.usb4java.*;

import javax.usb.*;
import javax.usb.event.UsbPipeDataEvent;
import javax.usb.event.UsbPipeErrorEvent;
import javax.usb.event.UsbPipeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

public class Main {

    private static final short VENDOR_ID = 0x04e8;
    private static final short PRODUCT_ID = 0x6860;
    private static long TIMEOUT = 5000;
    private static byte IN_ENDPOINT = (byte) 0x81;

    static UsbDevice usbDevice;

    public static UsbDevice findDevice(UsbHub hub, short vendorId, short productId)
    {
        for (UsbDevice device : (List<UsbDevice>) hub.getAttachedUsbDevices())
        {
            UsbDeviceDescriptor desc = device.getUsbDeviceDescriptor();

            if (desc.idVendor() == vendorId && desc.idProduct() == productId) {

                System.err.println("Found usbDevice " + desc.idVendor() + " " + desc.idProduct());
                return device;
            }

            if (device.isUsbHub()) {

                System.err.println("device.isUsbHub()" + " " + desc.idVendor() + " " + desc.idProduct());
                device = findDevice((UsbHub) device, vendorId, productId);

                if (device != null) {

                    return device;
                }
            }
        }

        return null;
    }


    public static UsbDevice getHygrometerDevice(UsbHub hub) {
        UsbDevice launcher = null;

        for (Object object : hub.getAttachedUsbDevices()) {
            UsbDevice device = (UsbDevice) object;
            if (device.isUsbHub()) {
                launcher = getHygrometerDevice((UsbHub) device);
                if (launcher != null)
                    return launcher;
            } else {
                UsbDeviceDescriptor desc = device.getUsbDeviceDescriptor();
                if (desc.idVendor() == VENDOR_ID && desc.idProduct() == PRODUCT_ID)
                    return device;
            }
        }
        return null;
    }

    public static char readKey() {
        try {
            String line = new BufferedReader(new InputStreamReader(System.in)).readLine();
            if (line.length() > 0)
                return line.charAt(0);
            return 0;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read key", e);
        }
    }

    public static ByteBuffer read(DeviceHandle handle, int size) {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(size).order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(handle, IN_ENDPOINT, buffer, transferred, TIMEOUT);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to read data", result);
        }
        System.out.println(transferred.get() + " bytes read from device");
        return buffer;
    }

    public static void main(String[] args) {

//        Context context = new Context();
//        int result = LibUsb.init(null);
//        if (result != LibUsb.SUCCESS) {
//            throw new LibUsbException("Unable to initialize libusb.", result);
//        }
//        DeviceList list = new DeviceList();
//        result = LibUsb.getDeviceList(null, list);
//        if (result < 0) throw new LibUsbException("Unable to get device list", result);
//        try {
//
//            // Iterate over all devices and scan for the right one
//            for (Device device: list) {
//
//                DeviceDescriptor descriptor = new DeviceDescriptor();
//                result = LibUsb.getDeviceDescriptor(device, descriptor);
//
//                if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to read device descriptor", result);
////                System.out.println(descriptor.idVendor()+" "+descriptor.idProduct());
//
//                if (descriptor.idVendor() == VENDOR_ID && descriptor.idProduct() == PRODUCT_ID) {
//
//                    System.err.println("Device 1256-26720 is connected");
//                    DeviceHandle handle = new DeviceHandle();
//                    result = LibUsb.open(device, handle);
//
//                    if (result < 0) {
//
//                        System.out.println(String.format("Unable to open device: %s. " + "Continuing without device handle.", LibUsb.strError(result)));
//                        handle = null;
//                    }
//                }
//            }
//        }
//        finally {
//            // Ensure the allocated device list is freed
//            LibUsb.freeDeviceList(list, true);
//        }

        try {

            UsbHub rootUsbHub = UsbHostManager.getUsbServices().getRootUsbHub();
            usbDevice = findDevice(rootUsbHub, VENDOR_ID, PRODUCT_ID);

            if (usbDevice == null) {
                System.err.println("Device not found");
            }

            System.err.println(usbDevice.getManufacturerString());


        } catch (UsbException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        UsbConfiguration configuration = usbDevice.getActiveUsbConfiguration();
        System.err.println(configuration.getUsbInterfaces());

        UsbInterface iface = configuration.getUsbInterface((byte) 0);
        System.err.println(iface.getUsbInterfaceDescriptor());
        List usbEndpoints = iface.getUsbEndpoints();

        for (Object p: usbEndpoints) {
            System.err.println(((UsbEndpoint)p).getUsbEndpointDescriptor());
        }


        try {
            iface.claim(new UsbInterfacePolicy() {

                public boolean forceClaim(UsbInterface usbInterface) {
                    return true;
                }});
        } catch (UsbException e) {
            e.printStackTrace();
        }


        UsbEndpoint endpoint = iface.getUsbEndpoint((byte) 0x82);
        UsbPipe pipe = endpoint.getUsbPipe();
        try {
            pipe.open();
        } catch (UsbException e) {
            e.printStackTrace();
        }

        try
        {
            byte[] data = new byte[8];
            int received = pipe.syncSubmit(data);
            System.out.println(received + " bytes received");
        } catch (UsbException e) {
            e.printStackTrace();
        } finally
        {
            try {
                pipe.close();
            } catch (UsbException e) {
                e.printStackTrace();
            }
        }
        try {
            iface.release();
        } catch (UsbException e) {
            e.printStackTrace();
        }

        pipe.addUsbPipeListener(new UsbPipeListener() {

            public void errorEventOccurred(UsbPipeErrorEvent event) {

                UsbException error = event.getUsbException();
            }

            public void dataEventOccurred(UsbPipeDataEvent event) {

                byte[] data = event.getData();
                System.err.println(data);
            }
        });

    }
}
