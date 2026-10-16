/* eslint-disable @typescript-eslint/no-explicit-any -- the service responses are loosely typed JSON */
import React, {useEffect, useState} from "react";
import {Collapse, Descriptions, Image, Modal, Progress, Space, Spin, Table, Tabs, Tag, Typography} from "antd";
import {checkImageUrl, FraudEventService} from "../services/FraudEventService";
import type {WorkflowDetails} from "../models/FraudEvent.ts";
import getRiskColor from "../config/ColorConfig.ts";

const {Text} = Typography;

interface Props {
    eventId: string | null;
    frontUri?: string;
    backUri?: string;
    onClose: () => void;
}

const RED = '#ff4d4f';
const ORANGE = '#faad14';
const GREEN = '#52c41a';
const GREY = '#8c8c8c';

const pct = (v: unknown) => `${Math.round(Number(v ?? 0) * 100)}%`;
const num = (v: unknown, digits = 2) => (v === null || v === undefined || v === '' ? '-' : Number(v).toFixed(digits));

const riskTagColor: Record<string, string> = {HIGH: 'red', MEDIUM: 'orange', LOW: 'blue', MINIMAL: 'green'};
const actionTagColor: Record<string, string> = {HOLD: 'red', MANUAL_REVIEW: 'orange', PASS: 'green'};
const severityColor: Record<string, string> = {CRITICAL: RED, HIGH: '#fa541c', MEDIUM: ORANGE, LOW: '#1890ff'};

/** Plain-language explanation of the reason codes the services return. */
const REASON_TEXT: Record<string, string> = {
    NEW_PAYOR_PAYEE_RELATIONSHIP: 'This payor and payee have not been seen together before',
    LAYOUT_ANOMALY: 'The check layout looks unusual (line structure differs from a normal check)',
    FONT_ANOMALY: 'The text sizes on the check are inconsistent, which can indicate editing',
    DUPLICATE_IMAGE: 'This check image matches an earlier deposit',
    OCR_AMOUNT_MISMATCH: 'The amount printed on the check does not match the deposited amount',
    MISSING_SIGNATURE: 'No signature was detected',
    MISSING_ENDORSEMENT: 'No endorsement was detected on the back',
};
/** Shown when the ML result carries no formula text (older results). */
const DEFAULT_FINAL_FORMULA = 'max(deposit_prob, 0.45 * deposit_prob + 0.55 * in_clearing_prob)';
const explain = (code: string) => REASON_TEXT[code] ?? code.replace(/_/g, ' ').toLowerCase();

/** Small coloured message box. */
const Notice: React.FC<{ color: string; title: string; text?: string }> = ({color, title, text}) => (
    <div style={{borderLeft: `4px solid ${color}`, background: '#fafafa', padding: '8px 12px', borderRadius: 4}}>
        <Text strong style={{color}}>{title}</Text>
        {text ? <div><Text type="secondary">{text}</Text></div> : null}
    </div>
);

const Stack: React.FC<{ gap?: number; children: React.ReactNode }> = ({gap = 4, children}) => (
    <div style={{display: 'flex', flexDirection: 'column', gap}}>{children}</div>
);

const statusTag = (completed: boolean, result: any) => {
    if (!completed) return <Tag>Pending</Tag>;
    if (result && String(result.status).toUpperCase() === 'FAILED') return <Tag color="red">Failed</Tag>;
    return <Tag color="green">Completed</Tag>;
};

const ReasonTags: React.FC<{ codes?: string[]; color?: string }> = ({codes, color = 'volcano'}) =>
    !codes || codes.length === 0
        ? <Text type="secondary">None</Text>
        : <Stack>
            {codes.map(c => (
                <span key={c}><Tag color={color}>{c}</Tag><Text type="secondary">{explain(c)}</Text></span>
            ))}
        </Stack>;

/** A 0..1 score as a bar; "bad" decides whether a high value is good or bad. */
const ScoreBar: React.FC<{ value: unknown; badWhen: 'high' | 'low'; label: string; hint: string }> =
    ({value, badWhen, label, hint}) => {
        const v = Number(value ?? 0);
        const bad = badWhen === 'high' ? v >= 0.5 : v < 0.5;
        return (
            <div style={{marginBottom: 8}}>
                <div style={{display: 'flex', justifyContent: 'space-between'}}>
                    <Text strong>{label}</Text>
                    <Text style={{color: bad ? RED : GREEN}}>{num(v)} {bad ? '⚠' : '✓'}</Text>
                </div>
                <Progress percent={Math.round(v * 100)} showInfo={false} size="small"
                          strokeColor={bad ? RED : GREEN}/>
                <Text type="secondary" style={{fontSize: 12}}>{hint}</Text>
            </div>
        );
    };

const Failed: React.FC<{ result: any }> = ({result}) => (
    <Notice color={RED} title="This service reported a failure" text={String(result?.error ?? 'No error message')}/>
);

// ---------------------------------------------------------------------------------------------------------------
// Consortium
// ---------------------------------------------------------------------------------------------------------------
const pick = (obj: any, key: string) => obj?.[key] ?? obj?.[key.toUpperCase()];

const ConsortiumBody: React.FC<{ result: any }> = ({result}) => {
    const d = result?.details ?? {};
    const counts = d.supportingLinkedCounts ?? {};
    const blend = d.explanation?.scoreBlend ?? {};
    const tokenRows = ['payee', 'payor', 'account', 'device', 'image']
        .map(k => ({key: k, ...(pick(counts, k) ?? {})}))
        .filter(r => r.tokenType);
    const flow = counts.bankFlow;
    const rel = counts.payorPayeeRelationship;

    return (
        <>
            <Descriptions size="small" column={1} bordered items={[
                {
                    key: 'score', label: 'Network risk score',
                    children: <Space>
                        <Progress type="circle" format={(p) => `${p}%`} size={44} percent={Math.round((d.score ?? 0) * 100)}
                                  strokeColor={getRiskColor(d.score ?? 0)}/>
                        <Text type="secondary">0 = never seen in the consortium, 1 = strongly linked to fraud</Text>
                    </Space>,
                },
                {key: 'reasons', label: 'Why', children: <ReasonTags codes={d.topReasons}/>},
            ]}/>

            <Text strong style={{display: 'block', margin: '12px 0 4px'}}>How often each identifier appeared across banks</Text>
            <Table size="small" pagination={false} rowKey="key" dataSource={tokenRows} columns={[
                {title: 'Identifier', dataIndex: 'key'},
                {title: '7d', dataIndex: 'appearances7d'},
                {title: '14d', dataIndex: 'appearances14d'},
                {title: '30d', dataIndex: 'appearances30d'},
                {
                    title: 'Banks (14d)', dataIndex: 'institutionCount14d',
                    render: (v: number) => <Text style={{color: v > 1 ? RED : undefined, fontWeight: v > 1 ? 600 : 400}}>{v}</Text>,
                },
                {
                    title: 'Frauds (30d)', dataIndex: 'fraudCount30d',
                    render: (v: number) => <Text style={{color: v > 0 ? RED : undefined, fontWeight: v > 0 ? 600 : 400}}>{v}</Text>,
                },
            ]}/>

            <Descriptions size="small" column={1} bordered style={{marginTop: 12}} items={[
                ...(rel ? [{
                    key: 'rel', label: 'Payor-payee relationship (30d)',
                    children: `${rel.relationshipAppearances30d} deposits, ${rel.relationshipFraudCount30d} fraud, ${rel.relationshipInstitutionCount30d} banks`,
                }] : []),
                ...(flow ? [{
                    key: 'flow', label: 'Bank flow (30d)',
                    children: `${flow.bankFlowAppearances30d} deposits, fraud rate ${pct(flow.bankFlowFraudRate30d)}`,
                }] : []),
                {
                    key: 'blend', label: 'Score blend',
                    children: `identifiers ${num(blend.maxTokenRisk)} (weight ${blend.maxTokenRiskWeight}), `
                        + `relationship ${num(blend.relationshipRisk)} (weight ${blend.relationshipRiskWeight}), `
                        + `bank flow ${num(blend.bankFlowRisk)} (weight ${blend.bankFlowRiskWeight})`,
                },
            ]}/>
        </>
    );
};

// ---------------------------------------------------------------------------------------------------------------
// Image
// ---------------------------------------------------------------------------------------------------------------
const ImageBody: React.FC<{ result: any }> = ({result}) => {
    const d = result?.details ?? {};
    const e = d.explanation ?? {};
    const h = e.hashing ?? {};
    const detected: number[] = e.ocrAmountsDetected ?? [];

    return (
        <>
            <ScoreBar label="Duplicate image" value={d.image_duplicate_score} badWhen="high"
                      hint="How closely this image matches an earlier deposit"/>
            <ScoreBar label="Layout anomaly" value={d.layout_anomaly_score} badWhen="high"
                      hint="How unusual the check's line structure is"/>
            <ScoreBar label="Font anomaly" value={d.font_anomaly_score} badWhen="high"
                      hint="Inconsistent text sizes can indicate altered text"/>
            <ScoreBar label="Signature present" value={d.signature_presence_score} badWhen="low"
                      hint="Strength of the signature in the lower-right area"/>
            <ScoreBar label="Endorsement present" value={d.endorsement_score} badWhen="low"
                      hint="Ink found on the back of the check"/>

            <Descriptions size="small" column={1} bordered style={{marginTop: 8}} items={[
                {
                    key: 'ocr', label: 'Amount on the check (OCR)',
                    children: <Space wrap>
                        <Tag color={d.ocr_amount_match ? 'green' : 'red'}>{d.ocr_amount_match ? 'Matches' : 'Does not match'}</Tag>
                        <Text>read: {detected.length ? detected.map(a => `$${a}`).join(', ') : 'nothing'}</Text>
                        <Text type="secondary">deposited: ${e.expectedAmount}</Text>
                    </Space>,
                },
                {key: 'reasons', label: 'Findings', children: <ReasonTags codes={d.reasonCodes}/>},
                {
                    key: 'dup', label: 'Duplicate check',
                    children: h.closest_event
                        ? <Text style={{color: RED}}>Closest earlier deposit {h.closest_event} (distance {h.closest_distance}
                            {h.duplicate_basis ? `, basis ${h.duplicate_basis}` : ''})</Text>
                        : <Text style={{color: GREEN}}>No similar earlier image</Text>,
                },
                ...(e.ocrTextPreview ? [{key: 'text', label: 'Text read from the image', children: <pre style={{margin: 0}}>{e.ocrTextPreview}</pre>}] : []),
            ]}/>
            {e.limitations?.length ? <Text type="secondary" style={{fontSize: 12, display: 'block', marginTop: 8}}>
                Note: {e.limitations.join('. ')}.
            </Text> : null}
        </>
    );
};

// ---------------------------------------------------------------------------------------------------------------
// Rules
// ---------------------------------------------------------------------------------------------------------------
const RULE_TEXT: Record<string, string> = {
    DUPLICATE_PRESENTMENT: 'The same check (routing, account and serial) was deposited before',
    HIGH_DEPOSIT_VELOCITY_7D: 'Unusually many deposits from this account in the last 7 days',
    NEW_DEVICE_FOR_ACCOUNT: 'This account has not deposited from this device before',
    HIGH_AMOUNT_CHECK: 'The amount is at or above the high-value threshold',
};

const RulesBody: React.FC<{ result: any }> = ({result}) => {
    const hits: any[] = Object.values(result?.details ?? {});
    if (hits.length === 0) {
        return <Notice color={GREEN} title="No rule was triggered"/>;
    }
    return (
        <Stack gap={8}>
            {hits.map((h, i) => (
                <div key={i} style={{
                    borderLeft: `4px solid ${severityColor[h.severity] ?? GREY}`,
                    background: '#fafafa', padding: '8px 12px', borderRadius: 4,
                }}>
                    <Space wrap>
                        <Tag color={severityColor[h.severity] ?? GREY}>{h.severity}</Tag>
                        <Text strong>{h.ruleCode}</Text>
                    </Space>
                    <div><Text>{RULE_TEXT[h.ruleCode] ?? ''}</Text></div>
                    <div>
                        {Object.entries(h.evidence ?? {}).map(([k, v]) => (
                            <Tag key={k}>{k}: {String(v)}</Tag>
                        ))}
                    </div>
                </div>
            ))}
        </Stack>
    );
};

// ---------------------------------------------------------------------------------------------------------------
// ML
// ---------------------------------------------------------------------------------------------------------------
const ContributionTable: React.FC<{ rows: any[]; title: string }> = ({rows, title}) => (
    <>
        <Text strong style={{display: 'block', margin: '12px 0 4px'}}>{title}</Text>
        {rows?.length ? (
            <Table size="small" pagination={false} rowKey="feature" dataSource={rows} columns={[
                {title: 'Signal', dataIndex: 'feature'},
                {title: 'Value', dataIndex: 'value', render: (v: unknown) => String(v)},
                {
                    title: 'Effect', dataIndex: 'contribution',
                    render: (c: number | undefined, r: any) => {
                        const v = c ?? r.approxContribution ?? 0;
                        return <Text style={{color: v > 0 ? RED : GREEN, fontWeight: 600}}>
                            {v > 0 ? '▲ raises risk' : '▼ lowers risk'} ({v > 0 ? '+' : ''}{num(v)})
                        </Text>;
                    },
                },
            ]}/>
        ) : <Text type="secondary">No signal changed the score noticeably</Text>}
    </>
);

const MlBody: React.FC<{ result: any }> = ({result}) => {
    const d = result?.details ?? {};
    const ex = d.explanation ?? {};
    const prob = (label: string, v: number) => (
        <div style={{textAlign: 'center'}}>
            <Progress type="circle" format={(p) => `${p}%`} size={64} percent={Math.round((v ?? 0) * 100)} strokeColor={getRiskColor(v ?? 0)}/>
            <div><Text type="secondary">{label}</Text></div>
        </div>
    );
    return (
        <>
            <Space size="large" wrap>
                {prob('At deposit', d.depositFraudProbability)}
                {prob('In clearing', d.inClearingFraudProbability)}
                {prob('Final', d.finalFraudProbability)}
            </Space>
            <Descriptions size="small" column={1} bordered style={{marginTop: 12}} items={[
                {key: 'band', label: 'Risk band', children: <Tag color={riskTagColor[d.calibratedRiskBand] ?? 'default'}>{d.calibratedRiskBand}</Tag>},
                {key: 'action', label: 'Recommended action', children: <Tag color={actionTagColor[d.recommendedAction] ?? 'default'}>{d.recommendedAction}</Tag>},
                {key: 'formula', label: 'How the final score is made', children: <Text code>{ex.scoreBlend?.finalFormula ?? DEFAULT_FINAL_FORMULA}</Text>},
            ]}/>
            <ContributionTable title="What moved the deposit score" rows={ex.depositFeatureContributions}/>
            <ContributionTable title="What moved the in-clearing score" rows={ex.inClearingFeatureContributions}/>
            {ex.contributionUnit ? <Text type="secondary" style={{fontSize: 12}}>{ex.contributionUnit}</Text> : null}
        </>
    );
};

// ---------------------------------------------------------------------------------------------------------------
// Modal
// ---------------------------------------------------------------------------------------------------------------
type Section = {
    key: string;
    title: string;
    completed: boolean;
    result: any;
    color: string;          // highlight colour of the section
    headline: string;       // one-line takeaway shown in the collapsed header
    body: React.ReactNode;
};

const buildSections = (w: WorkflowDetails): Section[] => {
    const cons = w.consortiumResult?.details ?? {};
    const img = w.imageResult?.details ?? {};
    const rules = Object.values(w.ruleResult?.details ?? {}) as any[];
    const ml = w.mlResult?.details ?? {};
    const failed = (r: any) => r && String(r.status).toUpperCase() === 'FAILED';
    const wrap = (r: any, body: React.ReactNode) => (failed(r) ? <Failed result={r}/> : body);

    const imageFindings = (img.reasonCodes ?? []).length + (img.ocr_amount_match === false ? 1 : 0);
    const worstRule = rules.reduce((worst, r) => ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].indexOf(r.severity)
        < ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].indexOf(worst) ? r.severity : worst, 'LOW');

    return [
        {
            key: 'ml', title: 'ML scoring', completed: w.mlCompleted, result: w.mlResult,
            color: failed(w.mlResult) ? RED : getRiskColor(ml.finalFraudProbability ?? 0),
            headline: ml.recommendedAction
                ? `${ml.recommendedAction} · final ${pct(ml.finalFraudProbability)} · ${ml.calibratedRiskBand}` : '',
            body: wrap(w.mlResult, <MlBody result={w.mlResult}/>),
        },
        {
            key: 'rules', title: 'Rules', completed: w.ruleCompleted, result: w.ruleResult,
            color: failed(w.ruleResult) ? RED : rules.length ? (severityColor[worstRule] ?? ORANGE) : GREEN,
            headline: rules.length ? `${rules.length} rule${rules.length > 1 ? 's' : ''} triggered` : 'No rule triggered',
            body: wrap(w.ruleResult, <RulesBody result={w.ruleResult}/>),
        },
        {
            key: 'image', title: 'Image analysis', completed: w.imageCompleted, result: w.imageResult,
            color: failed(w.imageResult) ? RED : imageFindings ? ORANGE : GREEN,
            headline: imageFindings ? `${imageFindings} finding${imageFindings > 1 ? 's' : ''}` : 'Nothing suspicious found',
            body: wrap(w.imageResult, <ImageBody result={w.imageResult}/>),
        },
        {
            key: 'consortium', title: 'Consortium network', completed: w.consortiumCompleted, result: w.consortiumResult,
            color: failed(w.consortiumResult) ? RED : getRiskColor(cons.score ?? 0),
            headline: cons.score !== undefined ? `network risk ${num(cons.score)}` : '',
            body: wrap(w.consortiumResult, <ConsortiumBody result={w.consortiumResult}/>),
        },
    ];
};

/** One check side, or a message when the file cannot be shown. */
const CheckSide: React.FC<{ title: string; uri?: string }> = ({title, uri}) => (
    <div style={{flex: '1 1 320px', minWidth: 0}}>
        <Text strong>{title}</Text>
        <div style={{marginTop: 8, border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, textAlign: 'center'}}>
            {uri
                ? <Image src={checkImageUrl(uri)} alt={title} style={{maxWidth: '100%'}}
                         fallback="data:image/svg+xml;utf8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='320' height='140'%3E%3Ctext x='50%25' y='50%25' fill='%238c8c8c' font-family='sans-serif' font-size='14' text-anchor='middle'%3EImage not available%3C/text%3E%3C/svg%3E"/>
                : <Text type="secondary">No image was provided.</Text>}
        </div>
        {uri && <Text type="secondary" style={{fontSize: 12, wordBreak: 'break-all'}}>{uri}</Text>}
    </div>
);

/** Front and back of the deposited check. Click an image to enlarge it. */
const ChequeImages: React.FC<{ frontUri?: string; backUri?: string }> = ({frontUri, backUri}) => (
    <Image.PreviewGroup>
        <div style={{display: 'flex', gap: 16, flexWrap: 'wrap'}}>
            <CheckSide title="Front" uri={frontUri}/>
            <CheckSide title="Back" uri={backUri}/>
        </div>
    </Image.PreviewGroup>
);

/** Loads and shows the details of one event. Mounted only while a popup is open, so its state starts fresh. */
const WorkflowDetailsContent: React.FC<{ eventId: string; frontUri?: string; backUri?: string }> = ({eventId, frontUri, backUri}) => {
    const [details, setDetails] = useState<WorkflowDetails | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        FraudEventService.getWorkflowDetails(eventId).then(d => {
            if (cancelled) return;
            setDetails(d);
            setLoading(false);
        });
        return () => {
            cancelled = true;
        };
    }, [eventId]);

    if (loading) return <div style={{textAlign: 'center', padding: 40}}><Spin/></div>;
    if (!details) return <Notice color={RED} title="Could not load the workflow details"/>;

    const sections = buildSections(details);
    const ml = details.mlResult?.details ?? {};
    const completedCount = [details.consortiumCompleted, details.imageCompleted, details.ruleCompleted, details.mlCompleted]
        .filter(Boolean).length;

    return (
        <>
            <div style={{marginBottom: 12}}>
                <Text type="secondary">Event {details.eventId}</Text>
                <div style={{marginTop: 8}}>
                    <Space size="middle" wrap>
                        <Progress type="circle" format={(p) => `${p}%`} size={56} percent={Math.round((ml.finalFraudProbability ?? 0) * 100)}
                                  strokeColor={getRiskColor(ml.finalFraudProbability ?? 0)}/>
                        {ml.calibratedRiskBand &&
                            <Tag color={riskTagColor[ml.calibratedRiskBand] ?? 'default'}>{ml.calibratedRiskBand}</Tag>}
                        {ml.recommendedAction &&
                            <Tag color={actionTagColor[ml.recommendedAction] ?? 'default'}>{ml.recommendedAction}</Tag>}
                        <Text>{completedCount} of 4 services completed</Text>
                    </Space>
                </div>
            </div>

            <Tabs
                items={[
                    {
                        key: 'analysis',
                        label: 'Analysis',
                        children: (
                <Collapse
                    defaultActiveKey={['ml']}
                    items={sections.map(s => ({
                        key: s.key,
                        style: {borderLeft: `5px solid ${s.completed ? s.color : '#d9d9d9'}`, marginBottom: 8},
                        label: (
                            <Space wrap>
                                <Text strong>{s.title}</Text>
                                {statusTag(s.completed, s.result)}
                                {s.completed && s.headline && <Text style={{color: s.color, fontWeight: 600}}>{s.headline}</Text>}
                            </Space>
                        ),
                        children: s.completed && s.result
                            ? s.body
                            : <Text type="secondary">This service has not responded yet.</Text>,
                    }))}
                />
                        ),
                    },
                    {
                        key: 'images',
                        label: 'Cheque images',
                        children: <ChequeImages frontUri={frontUri} backUri={backUri}/>,
                    },
                ]}
            />
        </>
    );
};

export const WorkflowDetailsModal: React.FC<Props> = ({eventId, frontUri, backUri, onClose}) => (
    <Modal
        open={eventId !== null}
        onCancel={onClose}
        footer={null}
        width={820}
        title="Fraud analysis details"
    >
        {eventId !== null && <WorkflowDetailsContent key={eventId} eventId={eventId} frontUri={frontUri} backUri={backUri}/>}
    </Modal>
);
