import {createRoot} from 'react-dom/client'
import App from './App.jsx'
import NamespacePage from './pages/NamespacePage';
import DeploymentListPage from './pages/DeploymentListPage';
import {BrowserRouter, Route, Routes} from "react-router-dom";


createRoot(document.getElementById('root')).render(
    <BrowserRouter>
        <Routes>
            <Route path="/" element={<App/>}>
                <Route index element={<NamespacePage/>}/>
                <Route path="/namespaces/:namespace" element={<DeploymentListPage/>}/>
            </Route>
        </Routes>
    </BrowserRouter>
)
