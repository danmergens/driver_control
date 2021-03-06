package com.raytheon.ooi.driver_interface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Concrete implementation of the Instrument Driver interface for ZMQ
 */

public class ZmqDriverInterface extends DriverInterface {
    private final ZMQ.Socket commandSocket;
    private final ZMQ.Socket eventSocket;
    private boolean keepRunning = true;
    private static Logger log = LoggerFactory.getLogger(ZmqDriverInterface.class);

    public ZmqDriverInterface(String host, int commandPort, int eventPort) {
        String commandUrl = String.format("tcp://%s:%d", host, commandPort);
        String eventUrl = String.format("tcp://%s:%d", host, eventPort);
        
        log.debug("Initialize ZmqDriverInterface");
        ZContext context = new ZContext();
        
        log.debug("Connecting to command port: {}", commandUrl);
        commandSocket = context.createSocket(ZMQ.REQ);
        commandSocket.connect(commandUrl);

        log.debug("Connecting to event port: {}", eventUrl);
        eventSocket = context.createSocket(ZMQ.SUB);
        eventSocket.connect(eventUrl);
        eventSocket.subscribe(new byte[0]);
        
        log.debug("Connected, starting event loop");
        Thread t = new Thread(this::eventLoop);
        t.setName("Event Loop");
        t.start();
        connected = true;
    }

    protected String _sendCommand(String command, int timeout) {
        commandSocket.send(command);
        commandSocket.setReceiveTimeOut(timeout * 1000);
        String reply = commandSocket.recvStr();
        if (reply == null)
            log.debug("Empty message received from command: {}", command);
        return reply;
    }

    protected void eventLoop() {
        try {
            while (keepRunning) {
                String reply = eventSocket.recvStr();

                if (reply != null) {
                    log.debug("REPLY = {}, numObservers = {}", reply, countObservers());
                    try {
                        setChanged();
                        notifyObservers(reply);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        log.error("Exception notifying observers: {}", e.getMessage());
                    }
                } else {
                    log.debug("Empty message received in event loop");
                }
            }
        } catch (Exception e) {
            log.debug("Exception in eventLoop: {}", e.getMessage());
        }
    }

    public void shutdown() {
        keepRunning = false;
        eventSocket.close();
        commandSocket.close();
        connected = false;
    }
}
