import {useState, useEffect, useRef} from 'react';
import {Client} from "@stomp/stompjs";
import type {DepositEvent} from "../models/FraudEvent.ts";
import {useDispatch} from 'react-redux';
import {addOrUpdateEventFromDepositResponse} from "../store/fraudEventsSlice";
import type {AppDispatch} from "../store";

export const useFraudWebSocket = () => {
    const dispatch = useDispatch<AppDispatch>();
    const [isConnected, setIsConnected] = useState(false);
    const [lastMessage, _] = useState<any>(null);
    const clientRef = useRef<Client | null>(null);

    useEffect(() => {
        if (clientRef.current) {
            return;
        }
        const client = new Client({
            brokerURL: "ws://localhost:8080/ws",

            debug: function (str: any) {
                console.log(str);
            },

            reconnectDelay: 5000,

            onConnect: function () {
                console.log("CONNECTED");
                setIsConnected(true);

                client.subscribe("/topic/single_service_completed", (message: { body: any; }) => {
                    console.log(JSON.parse(message.body));
                    // setLastMessage(message.body)
                    dispatch(addOrUpdateEventFromDepositResponse(JSON.parse(message.body).depositEventList[0]));
                });
                client.subscribe("/topic/deposit_response", (message: { body: any; }) => {
                    console.log(JSON.parse(message.body));
                    dispatch(addOrUpdateEventFromDepositResponse(JSON.parse(message.body).depositEventList[0]));
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

        return () => {
            try {
                if (clientRef.current) {
                    clientRef.current.deactivate(); // returns a Promise
                }
            } catch (e) {
                console.warn("deactivate error", e);
            } finally {
                clientRef.current = null;
            }
        };
    }, []);

    const publishDepositRequest = (depositEvent: DepositEvent) => {
        if (!clientRef.current || !clientRef.current.connected) {
            console.log("WebSocket not connected");
            return;
        }

        clientRef.current.publish({
            destination: "/app/deposit_request",
            body: JSON.stringify(depositEvent),
        });

        // clientRef.current.publish({
        //     destination: "/app/deposit_request",
        //     body: JSON.stringify({
        //         institutionId: "22222222-2222-4222-8222-222222222222",
        //         clearingInstitutionId:
        //             "22222222-2222-4222-8223-222222222222",
        //         channel: "mobile",
        //         depositTimestamp: "2026-04-24T10:30:00-02:30",
        //         amount: 7255.0,
        //         currency: "CAD",
        //         accountToken: "acct_demo_hmac_token",
        //         payeeToken: "payee_demo_hmac_token",
        //         payorToken: "payor_demo_hmac_token",
        //         deviceToken: "device_demo_NEW",
        //         region: "OUT_OF_REGION",
        //         checkSerialHash: "serial_hmac_hash",
        //         micrRoutingHash: "routing_hmac_hash",
        //         micrAccountHash: "micr_account_hmac_hash",
        //         imageFrontUri:
        //             "/path/front.png",
        //         imageBackUri:
        //             "/path/back.png",
        //     }),
        // });
    };

    return {isConnected, lastMessage, publishDepositRequest};
};