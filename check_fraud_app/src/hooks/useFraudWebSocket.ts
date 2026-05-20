import { useState, useEffect } from 'react';

export const useFraudWebSocket = () => {
  const [isConnected, setIsConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState<string | null>(null);

  useEffect(() => {
    const socket = new WebSocket('ws://localhost:8080/gs-guide-websocket');

    socket.onopen = () => {
      console.log('WebSocket connected');
      setIsConnected(true);

      // Send STOMP CONNECT frame
      const connectFrame = `CONNECT
accept-version:1.1,1.0
host:localhost

\0`;
      socket.send(connectFrame);
    };

    socket.onmessage = (event) => {
      console.log('WS message:', event.data);

      // Parse STOMP frame if needed, or pass raw message
      if (event.data.includes('MESSAGE')) {
        // Extract body from STOMP MESSAGE frame
        const bodyStart = event.data.indexOf('\n\n') + 2;
        const bodyEnd = event.data.lastIndexOf('\0');
        const body = event.data.substring(bodyStart, bodyEnd);
        setLastMessage(body);
      } else {
        setLastMessage(event.data);
      }
    };

    socket.onerror = (error) => {
      console.error('WebSocket error:', error);
      setIsConnected(false);
    };

    socket.onclose = () => {
      console.log('WebSocket closed');
      setIsConnected(false);
    };

    return () => {
      socket.close();
    };
  }, []);

  return { isConnected, lastMessage };
};