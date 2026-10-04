package com.nhom15.drawguess.client.network;

import com.nhom15.drawguess.common.protocol.Message;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TCPClient {
    private volatile Socket socket;
    private volatile ObjectOutputStream output;
    private volatile ObjectInputStream input;

    public boolean connect(String host, int port) {
        disconnect();
        Socket newSocket = new Socket();
        socket = newSocket;
        try {
            newSocket.connect(new InetSocketAddress(host, port), 5000);
            newSocket.setSoTimeout(10000);
            // Hai bên tạo output và flush trước khi chờ header của input.
            ObjectOutputStream newOutput = new ObjectOutputStream(newSocket.getOutputStream());
            newOutput.flush();
            ObjectInputStream newInput = new ObjectInputStream(newSocket.getInputStream());
            output = newOutput;
            input = newInput;
            return !newSocket.isClosed();
        } catch (IOException e) {
            System.err.println("Không thể kết nối tới Server: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public synchronized boolean sendMessage(Message message) {
        ObjectOutputStream currentOutput = output;
        if (!isConnected() || currentOutput == null) {
            return false;
        }
        try {
            currentOutput.reset();
            currentOutput.writeObject(message);
            currentOutput.flush();
            return true;
        } catch (IOException e) {
            System.err.println("Lỗi gửi Message: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public Message receiveMessage() {
        ObjectInputStream currentInput = input;
        if (!isConnected() || currentInput == null) {
            return null;
        }
        try {
            Object object = currentInput.readObject();
            if (object instanceof Message message) {
                return message;
            }
            disconnect();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Lỗi nhận Message: " + e.getMessage());
            disconnect();
        }
        return null;
    }

    public boolean setReadTimeout(int milliseconds) {
        Socket currentSocket = socket;
        if (currentSocket == null || currentSocket.isClosed()) {
            return false;
        }
        try {
            currentSocket.setSoTimeout(milliseconds);
            return true;
        } catch (IOException e) {
            disconnect();
            return false;
        }
    }

    public boolean isConnected() {
        Socket currentSocket = socket;
        return currentSocket != null && currentSocket.isConnected()
                && !currentSocket.isClosed();
    }

    public void disconnect() {
        Socket currentSocket = socket;
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException e) {
                System.err.println("Lỗi đóng kết nối: " + e.getMessage());
            }
        }
        output = null;
        input = null;
    }
}
