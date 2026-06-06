import React from 'react';
import {useParams} from 'react-router-dom';
import DeploymentList from '../components/DeploymentList';

const DeploymentListPage = () => {
    const {namespace} = useParams();

    return (
        <div>
            <h2>Namespace: {namespace}</h2>
            <DeploymentList namespace={namespace}/>
        </div>
    );
};

export default DeploymentListPage;