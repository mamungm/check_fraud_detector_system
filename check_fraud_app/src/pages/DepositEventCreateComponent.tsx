import React from "react";
import {Form, type FormInstance, Input, Select} from "antd";
import type {DepositEvent} from "../models/FraudEvent.ts";

const layout = {
    labelCol: {span: 8},
    wrapperCol: {span: 16},
};

interface DepositEventCreateComponentProps {
    form: FormInstance;
    publishDepositRequest: (event: DepositEvent) => void;
}

export const DepositEventCreateComponent: React.FC<DepositEventCreateComponentProps> = ({
                                                                                            form,
                                                                                            publishDepositRequest
                                                                                        }) => {
    const onFinish = (values: any) => {
        console.log("Creating deposit event with values = ", values);

        const event: DepositEvent = {
            institutionId: values.institutionId,
            clearingInstitutionId: values.clearingInstitutionId,
            channel: values.channel,
            depositTimestamp: values.depositTimestamp.toISOString(),
            amount: Number(values.amount),
            currency: values.currency,

            accountToken: values.accountToken,
            payeeToken: values.payeeToken,
            payorToken: values.payorToken,
            deviceToken: values.deviceToken,

            region: values.region,

            checkSerialHash: values.checkSerialHash,
            micrRoutingHash: values.micrRoutingHash,
            micrAccountHash: values.micrAccountHash,

            imageFrontUri: values.imageFrontUri,
            imageBackUri: values.imageBackUri,
        };
        publishDepositRequest(event);
    };

    return <Form
        {...layout}
        form={form}
        name="control-hooks"
        onFinish={onFinish}
        style={{maxWidth: 800}}
        initialValues={{
            "institutionId": "22222222-2222-4222-8222-222222222222",
            "clearingInstitutionId": "22222222-2222-4222-8223-222222222222",
            "channel": "mobile",
            "depositTimestamp": "2026-04-24T10:30:00-02:30",
            "amount": 7255.00,
            "currency": "CAD",
            "accountToken": "acct_demo_hmac_token",
            "payeeToken": "payee_demo_hmac_token",
            "payorToken": "payor_demo_hmac_token",
            "deviceToken": "device_demo_NEW",
            "region": "OUT_OF_REGION",
            "checkSerialHash": "serial_hmac_hash",
            "micrRoutingHash": "routing_hmac_hash",
            "micrAccountHash": "micr_account_hmac_hash",
            "imageFrontUri": "/Users/mamungm/Desktop/Reading/MUN/Semester_5/check_fraud_detector_system/synthetic-data/out/images/check_1_normal.png",
            "imageBackUri": "/Users/mamungm/Desktop/Reading/MUN/Semester_5/check_fraud_detector_system/synthetic-data/out/images/back_check_1_normal.png"
        }}
    >
        <Form.Item name="institutionId" label="Institution ID" rules={[{required: true}]}>
            <Input placeholder="Input institution UUID"/>
        </Form.Item>
        <Form.Item name="clearingInstitutionId" label="Clearing Institution ID" rules={[{required: true}]}>
            <Input placeholder="Input clearing institution UUID"/>
        </Form.Item>
        <Form.Item name="channel" label="Deposit Channel" rules={[{required: true}]}>
            <Select
                allowClear
                placeholder="Select an option"
                options={[
                    {label: 'mobile', value: 'mobile'},
                    {label: 'ATM', value: 'ATM'},
                    {label: 'branch', value: 'branch'},
                ]}
            />
        </Form.Item>
        <Form.Item name="depositTimestamp" label="Deposit time" rules={[{required: true}]}>
            <Input placeholder="Input deposit time"/>
        </Form.Item>
        <Form.Item name="amount" label="Deposit Amount" rules={[{required: true}]}>
            <Input placeholder="Input deposit amount"/>
        </Form.Item>
        <Form.Item name="currency" label="Currency" rules={[{required: true}]}>
            <Select
                allowClear
                placeholder="Select an option"
                options={[
                    {label: 'CAD', value: 'CAD'},
                    {label: 'USD', value: 'USD'},
                    {label: 'BDT', value: 'BDT'},
                ]}
            />
        </Form.Item>
        <Form.Item name="accountToken" label="Destination bank account" rules={[{required: true}]}>
            <Input placeholder="Destination bank account to deposit the cheque"/>
        </Form.Item>
        <Form.Item name="payeeToken" label="Payee bank account" rules={[{required: true}]}>
            <Input placeholder="The person or entity receiving the cheque"/>
        </Form.Item>
        <Form.Item name="payorToken" label="Payor bank account" rules={[{required: true}]}>
            <Input placeholder="The person or entity issued the cheque"/>
        </Form.Item>
        <Form.Item name="deviceToken" label="Device used for the deposit" rules={[{required: true}]}>
            <Input placeholder="The device used to perform the deposit"/>
        </Form.Item>
        <Form.Item name="region" label="Region" rules={[{required: true}]}>
            <Select
                allowClear
                placeholder="Select an option"
                options={[
                    {label: 'HOME_REGION', value: 'HOME_REGION'},
                    {label: 'IN_REGION', value: 'IN_REGION'},
                    {label: 'OUT_OF_REGION', value: 'OUT_OF_REGION'},
                    {label: 'INTERNATIONAL', value: 'INTERNATIONAL'},
                    {label: 'HIGH_RISK_REGION', value: 'HIGH_RISK_REGION'},
                    {label: 'UNKNOWN', value: 'UNKNOWN'},
                    {label: 'CROSS_BORDER', value: 'CROSS_BORDER'},
                ]}
            />
        </Form.Item>
        <Form.Item name="checkSerialHash" label="Cheque serial number" rules={[{required: true}]}>
            <Input placeholder="The cheque serial number"/>
        </Form.Item>
        <Form.Item name="micrRoutingHash" label="MICR routing number" rules={[{required: true}]}>
            <Input placeholder="Routing/transit number printed in the MICR line of the cheque"/>
        </Form.Item>
        <Form.Item name="micrAccountHash" label="MICR account number" rules={[{required: true}]}>
            <Input placeholder="Bank account number printed in the MICR line of the cheque"/>
        </Form.Item>
        <Form.Item name="imageFrontUri" label="Front image URI" rules={[{required: true}]}>
            <Input placeholder="The URI of the cheque front"/>
        </Form.Item>
        <Form.Item name="imageBackUri" label="Back image URI" rules={[{required: true}]}>
            <Input placeholder="The URI of the cheque back"/>
        </Form.Item>
    </Form>;
}