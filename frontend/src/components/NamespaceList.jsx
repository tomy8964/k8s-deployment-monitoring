import React, {useEffect, useState} from 'react';
import {fetchNamespaces} from '../api/namespaces';
import {Link, useLocation} from 'react-router-dom';
import styles from '../style/NamespaceList.module.css';
import {v4 as uuidv4} from "uuid";
import {cleanupSSEConnections} from "../api/sse.js";

const NamespaceList = () => {
    const [namespaces, setNamespaces] = useState([]);
    const location = useLocation();

    useEffect(() => {
        const getNamespaces = async () => {
            try {
                const data = await fetchNamespaces();
                setNamespaces(data);
            } catch (error) {
                console.error("Failed to fetch namespaces:", error);
            }
        };
        getNamespaces();
    }, []);

    useEffect(() => {
        const clientIdKey = 'sse_client_id';
        let clientId = localStorage.getItem(clientIdKey);
        if (!clientId) {
            clientId = uuidv4();
            localStorage.setItem('sse_client_id', clientId);
        }
    }, []);

    useEffect(() => {
        const cleanupSSE = async () => {
            const clientId = localStorage.getItem('sse_client_id');
            if (!clientId) return;

            try {
                console.debug(`[SSE Cleanup] Sending cleanup request for clientId: ${clientId}`);
                await cleanupSSEConnections(clientId);
                console.debug('[SSE Cleanup] Successful cleanup request.');
            } catch (error) {
                console.error('[SSE Cleanup] Failed to cleanup SSE connections:', error);
            }
        };

        return () => {
            if (location.pathname !== '/') {
                cleanupSSE();
            }
        };
    }, [location]);


    return (
        <div className={styles.container}>
            <h2 className={styles.title}>Namespaces</h2>
            <ul className={styles.list}>
                {namespaces.map((name) => (
                    <li key={name} className={styles.listItem}>
                        <Link to={`/namespaces/${name}`} className={styles.link}>
                            {name}
                        </Link>
                    </li>
                ))}
            </ul>
        </div>
    );
};

export default NamespaceList;