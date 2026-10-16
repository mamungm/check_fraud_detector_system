import React from "react";
import type {FraudEvent} from "../models/FraudEvent.ts";
import {Progress, Table, Tag, Tooltip} from "antd";
import getRiskColor from "../config/ColorConfig.ts";
import {CheckCircleOutlined, CloseCircleOutlined, WarningOutlined} from "@ant-design/icons";

interface FraudEventTableProps {
    events: FraudEvent[];
    onRowClick?: (event: FraudEvent) => void;
    onActionClick?: (event: FraudEvent) => void;
}

// One dot per analysis service, green once its flag in the workflow table is true.
const SERVICES: { key: 'imageCompleted' | 'consortiumCompleted' | 'mlCompleted' | 'ruleCompleted'; label: string }[] = [
    {key: 'imageCompleted', label: 'Image'},
    {key: 'consortiumCompleted', label: 'Consortium'},
    {key: 'mlCompleted', label: 'ML'},
    {key: 'ruleCompleted', label: 'Rules'},
];

export const FraudEventTableComponent: React.FC<FraudEventTableProps> = ({events, onRowClick, onActionClick}) => {
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
                    format={(percent) => `${percent}%`} // antd shows a check mark at 100% otherwise
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
            title: 'Services',
            key: 'services',
            render: (_: unknown, event: FraudEvent) => (
                <span style={{display: 'inline-flex', gap: 6}}>
                    {SERVICES.map(({key, label}) => {
                        const done = Boolean(event[key]);
                        return (
                            <Tooltip key={key} title={`${label}: ${done ? 'completed' : 'pending'}`}>
                                <span style={{
                                    width: 10,
                                    height: 10,
                                    borderRadius: '50%',
                                    display: 'inline-block',
                                    backgroundColor: done ? '#52c41a' : '#d9d9d9',
                                }}/>
                            </Tooltip>
                        );
                    })}
                </span>
            ),
            width: '8%',
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
            render: (action: string, event: FraudEvent) => {
                const icons: Record<string, React.ReactNode> = {
                    HOLD: <CloseCircleOutlined style={{color: '#ff4d4f'}}/>,
                    MANUAL_REVIEW: <WarningOutlined style={{color: '#faad14'}}/>,
                    PASS: <CheckCircleOutlined style={{color: '#52c41a'}}/>,
                };
                // Details are available once every service has answered.
                const done = Boolean(event.imageCompleted && event.consortiumCompleted
                    && event.mlCompleted && event.ruleCompleted);
                const icon = icons[action] || action;
                if (!done) {
                    return <span>{icon}</span>;
                }
                return (
                    <Tooltip title="Show the analysis of every service">
                        <a
                            onClick={(e) => {
                                e.stopPropagation(); // do not also open the row popup
                                onActionClick?.(event);
                            }}
                            style={{display: 'inline-flex', alignItems: 'center', gap: 6}}
                        >
                            {icon}
                            <span>Details</span>
                        </a>
                    </Tooltip>
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