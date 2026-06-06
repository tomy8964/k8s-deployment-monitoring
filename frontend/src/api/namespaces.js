import apiClient from './apiClient';

export const fetchNamespaces = async () => {
    const response = await apiClient.get('/api/namespaces');
    return response.data;
};