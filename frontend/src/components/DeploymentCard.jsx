import React, { useState } from "react";
import { updateDeploymentReplicas, updateDeploymentImage } from "../api/deployments.js";
import styles from "../style/DeploymentCard.module.css";

// 이미지명과 태그를 안전하게 파싱하는 순수 유틸리티 함수
const parseImageAndTag = (imageStr) => {
    if (!imageStr) return { name: "unknown", tag: "latest" };
    const parts = imageStr.split(":");
    const name = parts[0];
    const tag = parts.slice(1).join(":") || "latest";
    return { name, tag };
};

const DeploymentCard = ({ deployment, namespace }) => {
    const { name, replicas, image, status, statusMessage } = deployment;

    // 이미지 상세 파싱
    const { name: imageName, tag: currentTag } = parseImageAndTag(image);

    // 수정 상태 관리
    const [editingReplicas, setEditingReplicas] = useState(false);
    const [editingTag, setEditingTag] = useState(false);

    // 입력값 상태
    const [newReplicas, setNewReplicas] = useState(replicas);
    const [newTag, setNewTag] = useState(currentTag);

    // 프리미엄 피드백 Toast 알림 상태
    const [toast, setToast] = useState({ show: false, message: "", type: "success" });

    const showToast = (message, type = "success") => {
        setToast({ show: true, message, type });
        setTimeout(() => {
            setToast((prev) => ({ ...prev, show: false }));
        }, 3000);
    };

    // 레플리카 업데이트 요청
    const handleUpdateReplicas = async () => {
        const replicaVal = parseInt(newReplicas, 10);
        if (isNaN(replicaVal) || replicaVal < 0) {
            showToast("Replicas must be 0 or a positive number.", "error");
            return;
        }

        try {
            await updateDeploymentReplicas(namespace, name, replicaVal);
            showToast("Replicas updated successfully!", "success");
            setEditingReplicas(false);
        } catch (error) {
            console.error("Failed to update replicas:", error);
            showToast("Failed to update replicas.", "error");
        }
    };

    // 이미지 태그 업데이트 요청
    const handleUpdateTag = async () => {
        if (!newTag || newTag.trim() === "") {
            showToast("Image tag cannot be empty.", "error");
            return;
        }

        try {
            await updateDeploymentImage(namespace, name, newTag.trim());
            showToast("Image tag updated successfully!", "success");
            setEditingTag(false);
        } catch (error) {
            console.error("Failed to update image tag:", error);
            showToast("Failed to update image tag.", "error");
        }
    };

    // 실패 이유와 메시지를 추출하는 함수
    const getFailedReasons = () => {
        if (!status?.conditions) return null;

        const failedConditions = status.conditions.filter(
            (condition) =>
                condition.type === "Progressing" &&
                condition.reason === "ProgressDeadlineExceeded"
        );

        return failedConditions.map((condition, index) => ({
            reason: condition.reason,
            message: condition.message,
            id: index,
        }));
    };

    const failedReasons = getFailedReasons();

    return (
        <div className={styles.card}>
            {/* 헤더: Deployment 이름 및 상태 */}
            <div className={styles.header}>
                <strong className={styles.name}>{name}</strong>
                <span className={`${styles.status} ${getStatusClass(statusMessage)}`}>
                    {statusMessage}
                </span>
            </div>

            {/* 내용: 이미지 태그 및 레플리카 */}
            <div className={styles.content}>
                {/* 이미지 태그 업데이트 */}
                <p>
                    <strong>Image:</strong> {imageName}: {/* 이미지명 고정 */}
                    {editingTag ? (
                        <>
                            <input
                                type="text"
                                value={newTag}
                                onChange={(e) => setNewTag(e.target.value)}
                                className={styles.input}
                            />
                            <button
                                onClick={handleUpdateTag}
                                className={styles.saveButton}
                            >
                                Save
                            </button>
                            <button
                                onClick={() => setEditingTag(false)}
                                className={styles.cancelButton}
                            >
                                Cancel
                            </button>
                        </>
                    ) : (
                        <>
                            {currentTag}
                            <button
                                onClick={() => setEditingTag(true)}
                                className={styles.editButton}
                            >
                                Edit
                            </button>
                        </>
                    )}
                </p>

                {/* 레플리카 업데이트 */}
                <p>
                    <strong>Replicas:</strong>{" "}
                    {editingReplicas ? (
                        <>
                            <input
                                type="number"
                                min="0"
                                value={newReplicas}
                                onChange={(e) => setNewReplicas(e.target.value)}
                                className={styles.input}
                            />
                            <button
                                onClick={handleUpdateReplicas}
                                className={styles.saveButton}
                            >
                                Save
                            </button>
                            <button
                                onClick={() => setEditingReplicas(false)}
                                className={styles.cancelButton}
                            >
                                Cancel
                            </button>
                        </>
                    ) : (
                        <>
                            {status?.readyReplicas || 0} / {replicas}{" "}
                            <button
                                onClick={() => setEditingReplicas(true)}
                                className={styles.editButton}
                            >
                                Edit
                            </button>
                        </>
                    )}
                </p>

                {/* 상태 정보 추가 */}
                {status && (
                    <ul className={styles.statusList}>
                        <li>
                            <strong>Available:</strong> {status.availableReplicas || 0}
                        </li>
                        <li>
                            <strong>Updated:</strong> {status.updatedReplicas || 0}
                        </li>
                        <li>
                            <strong>Observed:</strong> {status.observedGeneration || "-"}
                        </li>
                    </ul>
                )}

                {/* Failed 상태일 경우 실패 이유 표시 */}
                {statusMessage === "Failed" && failedReasons && (
                    <div className={styles.failedDetails}>
                        <h4 className={styles.failedTitle}>Failure Details</h4>
                        <ul>
                            {failedReasons.map(({ reason, message, id }) => (
                                <li key={id} className={styles.failedReason}>
                                    <strong>Reason:</strong> {reason}
                                    <br />
                                    <strong>Message:</strong> {message}
                                </li>
                            ))}
                        </ul>
                    </div>
                )}
            </div>

            {/* Custom Toast 알림 팝업 */}
            {toast.show && (
                <div
                    className={`${styles.toast} ${styles.toastShow} ${
                        toast.type === "success" ? styles.toastSuccess : styles.toastError
                    }`}
                >
                    {toast.message}
                </div>
            )}
        </div>
    );
};

// 상태 클래스 처리
function getStatusClass(deploymentStatus) {
    switch (deploymentStatus) {
        case "Running":
            return styles.runningStatus;
        case "Progressing":
            return styles.progressingStatus;
        case "Failed":
            return styles.failedStatus;
        default:
            return styles.unknownStatus;
    }
}

export default DeploymentCard;