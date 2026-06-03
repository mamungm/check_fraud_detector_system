import React from "react";
import type {FraudEvent} from "../models/FraudEvent.ts";
import {Progress, Table, Tag} from "antd";
import getRiskColor from "../config/ColorConfig.ts";
import {CheckCircleOutlined, CloseCircleOutlined, WarningOutlined} from "@ant-design/icons";

interface FraudEventTableProps {
    events: FraudEvent[];
    onRowClick?: (event: FraudEvent) => void;
}

export const FraudEventTableComponent: React.FC<FraudEventTableProps> = ({events, onRowClick}) => {
    const columns = [
        {
            title: 'Event ID',
            dataIndex: 'eventId',
            key: 'eventId',
            width: '10%'
        },
        {
            title: 'Institution',
            dataIndex: 'institutionId',
            key: 'institutionId',
            width: '10%',
        },
        {
            title: 'Amount',
            dataIndex: 'amount',
            key: 'amount',
            render: (amount: number) => amount ? `$${amount.toFixed(2)}` : '',
            width: '8%',
        },
        {
            title: 'Channel',
            dataIndex: 'channel',
            key: 'channel',
            width: '6%',
        },
        {
            title: 'Fraud Score',
            dataIndex: 'finalFraudProbability',
            key: 'finalFraudProbability',
            render: (score: number) => (
                <Progress
                    type="circle"
                    percent={Math.round(score * 100)}
                    width={40}
                    strokeColor={getRiskColor(score)}
                />
            ),
            width: '8%',
        },
        {
            title: 'Risk Band',
            dataIndex: 'calibratedRiskBand',
            key: 'calibratedRiskBand',
            render: (band: string) => getRiskTag(band),
            width: '8%',
        },
        {
            title: 'Status',
            dataIndex: 'workflow',
            key: 'workflow',
            width: '8%'
        },
        {
            title: 'Created At',
            dataIndex: 'createdAt',
            key: 'createdAt',
            render: (ts: string) => new Date(ts).toLocaleString(),
            width: '13%',
            ellipsis: true,
        },
        {
            title: 'Action',
            dataIndex: 'recommendedAction',
            key: 'recommendedAction',
            render: (action: string) => {
                const icons: Record<string, React.ReactNode> = {
                    HOLD: <CloseCircleOutlined style={{color: '#ff4d4f'}}/>,
                    MANUAL_REVIEW: <WarningOutlined style={{color: '#faad14'}}/>,
                    PASS: <CheckCircleOutlined style={{color: '#52c41a'}}/>,
                };
                return (
                    <span>{icons[action] || action}</span>
                );
            },
            width: '10%',
        }
    ];

    const getRiskTag = (band: string) => {
        const colors: Record<string, string> = {
            HIGH: 'red',
            MEDIUM: 'orange',
            LOW: 'blue',
            MINIMAL: 'green',
        };
        return <Tag color={colors[band] || 'default'}>{band}</Tag>;
    };

    return <Table
        columns={columns}
        dataSource={events.map((e, i) => ({...e, key: i}))}
        pagination={{pageSize: 10}}
        onRow={(record) => ({
            onClick: () => {
                if (onRowClick) {
                    onRowClick(record);
                }
            },
            style: {cursor: 'pointer'},
        })}
        tableLayout="fixed"
    />;
}