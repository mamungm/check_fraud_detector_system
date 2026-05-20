const getRiskColor = (probability: number): string => {
    if (probability >= 0.85) return '#ff4d4f'; // HIGH - red
    if (probability >= 0.60) return '#faad14'; // MEDIUM - orange
    if (probability >= 0.30) return '#1890ff'; // LOW - blue
    return '#52c41a'; // MINIMAL - green
  };

export default getRiskColor;