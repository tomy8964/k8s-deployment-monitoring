import apiClient from './apiClient';

const baseURL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const fetchDeployments = async (namespace) => {
    const response = await apiClient.get(`/api/deployments/${namespace}`);
    return response.data;
};

export const watchDeployments = (namespace, clientId) => {
    return new EventSource(`${baseURL}/api/deployments/${namespace}/watch/${clientId}`);
};

export const updateDeploymentReplicas = async (namespace, deploymentName, replicas) => {
    const response = await apiClient.post(`/api/deployments/${namespace}/${deploymentName}/replicas/${replicas}`, {replicas});
    return response.data;
};

export const updateDeploymentImage = async (namespace, deploymentName, image) => {
    const response = await apiClient.post(`/api/deployments/${namespace}/${deploymentName}/image/${image}`, {image});
    return response.data;
};
