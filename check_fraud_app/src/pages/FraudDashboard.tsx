import React, {useState, useEffect} from 'react';
import {Layout, Card, Row, Col, Statistic, Tag, Progress, Empty, Button, Modal, Form} from 'antd';
import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    PieChart,
    Pie,
    Cell
} from 'recharts';
import {WarningOutlined} from '@ant-design/icons';
import {useFraudWebSocket} from '../hooks/useFraudWebSocket';
import getRiskColor from "../config/ColorConfig.ts";
import {useDispatch, useSelector} from "react-redux";
import type {RootState, AppDispatch} from "../store";
import {fetchAllEvents, addEvent} from "../store/fraudEventsSlice";
import type {DepositEvent, FraudEvent} from "../models/FraudEvent.ts";
import {FraudEventTableComponent} from "./FraudEventTableComponent.tsx";
import {DepositEventCreateComponent} from "./DepositEventCreateComponent.tsx";

const {Header, Content} = Layout;

export const FraudDashboard: React.FC = () => {
    const dispatch = useDispatch<AppDispatch>();
    const events = useSelector((s: RootState) => s.fraudEvents.events);
    const loading = useSelector((s: RootState) => s.fraudEvents.loading);
    // const error = useSelector((s: RootState) => s.fraudEvents.error);

    const [selectedEvent, setSelectedEvent] = useState<FraudEvent | null>(null);
    const [modalVisible, setModalVisible] = useState(false);
    const {isConnected, lastMessage, publishDepositRequest} = useFraudWebSocket();

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [depositEventCreateForm] = Form.useForm();

    // Handle incoming WebSocket messages
    useEffect(() => {
        if (!lastMessage) return;
        try {
            const evt: FraudEvent = JSON.parse(lastMessage);
            dispatch(addEvent(evt));
        } catch (e) {
            console.error("Failed to parse WS message", e);
        }
    }, [lastMessage, dispatch]);

    useEffect(() => {
        dispatch(fetchAllEvents());
    }, [dispatch]);

    const showModal = () => {
        setIsModalOpen(true);
    };

    const handleSubmit = () => {
        // setIsModalOpen(false);
        depositEventCreateForm.submit();
    };

    const handleCancel = () => {
        setIsModalOpen(false);
    };

    const handleRowClick = (record: FraudEvent) => {
        setSelectedEvent(record);
        setModalVisible(true);
    };

    const handlePublishDepositRequest = (event: DepositEvent) => {
        publishDepositRequest(event);
        // dispatch(fetchAllEvents());
        setIsModalOpen(false);
    };

    const highRiskCount = events.filter(e => e.finalFraudProbability >= 0.85).length;
    const mediumRiskCount = events.filter(e => e.finalFraudProbability >= 0.60 && e.finalFraudProbability < 0.85).length;
    const lowRiskCount = events.filter(e => e.finalFraudProbability >= 0.30 && e.finalFraudProbability < 0.60).length;

    const riskDistribution = [
        {name: 'HIGH', value: highRiskCount, fill: '#ff4d4f'},
        {name: 'MEDIUM', value: mediumRiskCount, fill: '#faad14'},
        {name: 'LOW', value: lowRiskCount, fill: '#1890ff'},
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

    const avgScore = events.length > 0 ? (events.reduce((sum, e) => sum + e.finalFraudProbability, 0) / events.length).toFixed(3) : '0.000';

    return (
        <Layout style={{
            padding: '24px',
            maxWidth: '1800px',
            margin: '0 auto',
            width: '100%',
            boxSizing: 'border-box',
        }}>
            <Header style={{background: '#001529', color: 'white', padding: '0 24px'}}>
                <h1 style={{color: 'white', margin: 0}}>
                    Fraud Detection Dashboard
                    {isConnected && <Tag color="green" style={{marginLeft: '16px'}}>Live</Tag>}
                    {!isConnected && <Tag color="red" style={{marginLeft: '16px'}}>Offline</Tag>}
                </h1>
            </Header>

            <Content style={{padding: '24px'}}>
                {/* Key Metrics */}
                <Row gutter={16} style={{marginBottom: '24px'}}>
                    <Col xs={24} sm={12} md={6}>
                        <Card>
                            <Statistic
                                title="Total Events"
                                value={events.length}
                                prefix={<span>📊</span>}
                            />
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} md={6}>
                        <Card>
                            <Statistic
                                title="High Risk"
                                value={highRiskCount}
                                valueStyle={{color: '#ff4d4f'}}
                                prefix={<WarningOutlined/>}
                            />
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} md={6}>
                        <Card>
                            <Statistic
                                title="Avg Score"
                                value={avgScore}
                                precision={3}
                                suffix="/ 1.0"
                            />
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} md={6}>
                        <Card>
                            <Statistic
                                title="Connection Status"
                                value={isConnected ? 'Connected' : 'Disconnected'}
                                valueStyle={{color: isConnected ? '#52c41a' : '#ff4d4f'}}
                            />
                        </Card>
                    </Col>
                </Row>

                {/* Charts */}
                <Row gutter={16} style={{marginBottom: '24px'}}>
                    <Col xs={24} md={12}>
                        <Card title="Risk Distribution" loading={loading}>
                            <ResponsiveContainer width="100%" height={300}>
                                <PieChart>
                                    <Pie
                                        data={riskDistribution}
                                        cx="50%"
                                        cy="50%"
                                        labelLine={false}
                                        label={({name, value}) => `${name}: ${value}`}
                                        outerRadius={80}
                                        fill="#8884d8"
                                        dataKey="value"
                                    >
                                        {riskDistribution.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={entry.fill}/>
                                        ))}
                                    </Pie>
                                    <Tooltip/>
                                </PieChart>
                            </ResponsiveContainer>
                        </Card>
                    </Col>
                    <Col xs={24} md={12}>
                        <Card title="Recent Fraud Scores (Last 10)" loading={loading}>
                            <ResponsiveContainer width="100%" height={300}>
                                <LineChart data={events.slice(0, 10).reverse()}>
                                    <CartesianGrid strokeDasharray="3 3"/>
                                    <XAxis dataKey="eventId" tick={false}/>
                                    <YAxis domain={[0, 1]}/>
                                    <Tooltip/>
                                    <Line
                                        type="monotone"
                                        dataKey="finalFraudProbability"
                                        stroke="#1890ff"
                                        dot={{fill: '#1890ff', r: 4}}
                                    />
                                </LineChart>
                            </ResponsiveContainer>
                        </Card>
                    </Col>
                </Row>

                {/* Events Table */}
                <Card title="Real-Time Fraud Events" loading={loading} extra={
                    <div>
                        <Button
                            type="primary"
                            onClick={showModal}
                        >
                            Post a clearing cheque
                        </Button>
                        <Modal
                            title="Basic Modal"
                            closable={{'aria-label': 'Custom Close Button'}}
                            okText={"Submit"}
                            width={{
                                xs: '90%',
                                sm: '80%',
                                md: '70%',
                                lg: '60%',
                                xl: '800px',
                                xxl: '800px',
                            }}
                            open={isModalOpen}
                            onOk={handleSubmit}
                            onCancel={handleCancel}
                        >
                            <DepositEventCreateComponent form={depositEventCreateForm}
                                                         publishDepositRequest={handlePublishDepositRequest}/>
                        </Modal>
                    </div>
                }>
                    {events.length === 0 ? (
                        <Empty description="No events yet. Waiting for data..."/>
                    ) : (
                        <FraudEventTableComponent
                            events={events}
                            onRowClick={handleRowClick}/>
                    )}
                </Card>
            </Content>

            {/* Event Detail Modal */}
            <Modal
                title={`Event Details: ${selectedEvent?.eventId}`}
                visible={modalVisible}
                onCancel={() => setModalVisible(false)}
                footer={null}
                width={700}
            >
                {selectedEvent && (
                    <div>
                        <Row gutter={16}>
                            <Col span={12}>
                                <strong>Institution ID:</strong> {selectedEvent.institutionId}
                            </Col>
                            <Col span={12}>
                                <strong>Amount:</strong> ${selectedEvent.amount.toFixed(2)}
                            </Col>
                        </Row>
                        <Row gutter={16} style={{marginTop: '12px'}}>
                            <Col span={12}>
                                <strong>Channel:</strong> {selectedEvent.channel}
                            </Col>
                            <Col span={12}>
                                <strong>Timestamp:</strong> {new Date(selectedEvent.timestamp).toLocaleString()}
                            </Col>
                        </Row>
                        <Row gutter={16} style={{marginTop: '12px'}}>
                            <Col span={12}>
                                <strong>Deposit Fraud Probability:</strong>{' '}
                                <Progress
                                    type="circle"
                                    percent={Math.round(selectedEvent.depositFraudProbability * 100)}
                                    width={50}
                                    strokeColor={getRiskColor(selectedEvent.depositFraudProbability)}
                                />
                            </Col>
                            <Col span={12}>
                                <strong>Final Fraud Probability:</strong>{' '}
                                <Progress
                                    type="circle"
                                    percent={Math.round(selectedEvent.finalFraudProbability * 100)}
                                    width={50}
                                    strokeColor={getRiskColor(selectedEvent.finalFraudProbability)}
                                />
                            </Col>
                        </Row>
                        <Row gutter={16} style={{marginTop: '12px'}}>
                            <Col span={12}>
                                <strong>Risk Band:</strong> {getRiskTag(selectedEvent.calibratedRiskBand)}
                            </Col>
                            <Col span={12}>
                                <strong>Recommended Action:</strong> {selectedEvent.recommendedAction}
                            </Col>
                        </Row>
                    </div>
                )}
            </Modal>
        </Layout>
    );
};

export default FraudDashboard;