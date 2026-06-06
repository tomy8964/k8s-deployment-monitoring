import React, {useEffect, useRef, useState} from 'react';
import DeploymentCard from './DeploymentCard';
import {watchDeployments, fetchDeployments} from '../api/deployments';
import styles from '../style/DeploymentList.module.css';
import { v4 as uuidv4 } from 'uuid';

const DeploymentList = ({namespace}) => {
    const [deployments, setDeployments] = useState([]);
    const [error, setError] = useState(null);
    const eventSourceRef = useRef(null);

    const determineDeploymentStatus = (deployment) => {
        const {replicas, status = {}} = deployment;
        const {availableReplicas = 0, readyReplicas = 0, updatedReplicas = 0, conditions = []} = status;

        let currentStatus = "Running";

        if (!Array.isArray(conditions)) {
            return "Unknown";
        }

        const progressingCondition = conditions.find((cond) => cond.type === "Progressing");
        if (progressingCondition) {
            if (progressingCondition.reason === "ProgressDeadlineExceeded" && progressingCondition.status === "False") {
                return "Failed";
            }
        }

        if (availableReplicas < replicas || readyReplicas < replicas) {
            currentStatus = "Progressing";
        }
        if (updatedReplicas < replicas) {
            currentStatus = "Progressing";
        }

        return currentStatus;
    };

    const loadDeployments = async () => {
        try {
            const initialData = await fetchDeployments(namespace);
            const enhancedData = initialData.map((deployment) => ({
                ...deployment,
                statusMessage: determineDeploymentStatus(deployment),
            }));
            setDeployments(enhancedData);
            console.log('✔️ Initial deployments loaded:', enhancedData);
        } catch (err) {
            setError('Failed to fetch deployments.');
            console.error('❌ Error fetching initial data:', err);
        }
    };

    useEffect(() => {
        const clientIdKey = 'sse_client_id';
        let clientId = localStorage.getItem(clientIdKey);
        if (!clientId) {
            clientId = uuidv4();
            localStorage.setItem('sse_client_id', clientId);
        }

        let eventSource = null;

        const startSSEConnection = () => {
            eventSource = watchDeployments(namespace, clientId);
            eventSourceRef.current = eventSource;

            eventSource.onopen = () => {
                console.log('✔️ SSE connection opened:', namespace);
            };

            eventSource.onmessage = (event) => {
                try {
                    const parsedData = JSON.parse(event.data);
                    console.log('✅ Parsed SSE message:', parsedData);

                    const {type, name} = parsedData;

                    if (type === 'MODIFIED' || type === 'ADDED') {
                        setDeployments((prev) => {
                            const existingIndex = prev.findIndex((d) => d.name === name);
                            let updatedDeployment = {
                                ...parsedData,
                                statusMessage: determineDeploymentStatus(parsedData),
                            };

                            if (existingIndex !== -1) {
                                const updatedDeployments = [...prev];
                                updatedDeployments[existingIndex] = updatedDeployment;
                                console.log('🔄 Deployment updated:', name, updatedDeployment);
                                return updatedDeployments;
                            } else {
                                console.log('➕ New deployment added:', name);
                                return [...prev, updatedDeployment];
                            }
                        });
                    } else if (type === 'DELETED') {
                        console.log('❌ Deployment deleted:', name);
                        setDeployments((prev) => prev.filter((d) => d.name !== name));
                    }
                } catch (error) {
                    console.error('❌ Error parsing SSE data:', error);
                }
            };

            eventSource.onerror = (err) => {
                setError('Failed to connect to SSE server.');
                console.error('❌ SSE connection error:', err);
                eventSource.close();
            };

            console.log('🌐 Event source initialized');
        };

        loadDeployments();
        startSSEConnection();

        return () => {
            console.log('🔌 Cleaning up SSE connection...');
            if (eventSourceRef.current) {
                eventSourceRef.current.close();
                eventSourceRef.current = null;
            }
        };
    }, [namespace]);

    if (error) {
        return <div className={styles.errorMessage}>{error}</div>;
    }

    return (
        <div className={styles.listContainer}>
            <h2 className={styles.pageTitle}>Deployments in Namespace: {namespace}</h2>

            {deployments.length > 0 ? (
                deployments.map((deployment) => (
                    <DeploymentCard
                        key={deployment.name}
                        deployment={deployment}
                        namespace={namespace}
                        status={deployment.statusMessage}
                    />
                ))
            ) : (
                <p className={styles.noDataText}>No deployments found in this namespace.</p>
            )}
        </div>
    );
};

export default DeploymentList;