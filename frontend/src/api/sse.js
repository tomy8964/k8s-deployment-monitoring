import apiClient from './apiClient';

export const cleanupSSEConnections = async (clientId) => {
    const response = await apiClient.post(`/api/sse/cleanup/${clientId}`);
    return response.data;
};