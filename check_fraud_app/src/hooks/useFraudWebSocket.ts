import {useState, useEffect, useRef} from 'react';
import {Client} from "@stomp/stompjs";

export const useFraudWebSocket = () => {
    const [isConnected, setIsConnected] = useState(false);
    const [lastMessage, _] = useState<any>(null);
    const clientRef = useRef<Client | null>(null);

    useEffect(() => {
        const client = new Client({
            brokerURL: "ws://localhost:8080/ws",

            debug: function (str: any) {
                console.log(str);
            },

            reconnectDelay: 5000,

            onConnect: function () {
                console.log("CONNECTED");
                setIsConnected(true);

                client.subscribe("/topic/deposit_verification", (message: { body: any; }) => {
                    console.log(message.body);
                    // setLastMessage(message.body)
                });
            },

            onStompError: function (frame: any) {
                console.log(frame);
            },

            onWebSocketError: function (error: any) {
                console.log(error);
            },
        });

        clientRef.current = client;

        client.activate();
    }, []);

    const publishDepositRequest = () => {
        if (!clientRef.current || !clientRef.current.connected) {
            console.log("WebSocket not connected");
            return;
        }

        clientRef.current.publish({
            destination: "/app/deposit_request",
            body: JSON.stringify({
                institutionId: "22222222-2222-4222-8222-222222222222",
                clearingInstitutionId:
                    "22222222-2222-4222-8223-222222222222",
                channel: "mobile",
                depositTimestamp: "2026-04-24T10:30:00-02:30",
                amount: 7255.0,
                currency: "CAD",
                accountToken: "acct_demo_hmac_token",
                payeeToken: "payee_demo_hmac_token",
                payorToken: "payor_demo_hmac_token",
                deviceToken: "device_demo_NEW",
                region: "OUT_OF_REGION",
                checkSerialHash: "serial_hmac_hash",
                micrRoutingHash: "routing_hmac_hash",
                micrAccountHash: "micr_account_hmac_hash",
                imageFrontUri:
                    "/path/front.png",
                imageBackUri:
                    "/path/back.png",
            }),
        });
    };

    return {isConnected, lastMessage, publishDepositRequest};
};