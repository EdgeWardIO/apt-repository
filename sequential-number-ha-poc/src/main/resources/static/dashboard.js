// Dashboard JavaScript for Sequential Number Generation System
// Handles real-time updates, sequence generation, demonstrations, and monitoring

class SequenceDashboard {
    constructor() {
        this.sequenceHistory = [];
        this.charts = {};
        this.stats = {};
        this.isAutoRefreshing = false;
        
        // Initialize dashboard
        this.initializeCharts();
        this.startAutoRefresh();
        this.loadInitialData();
        
        console.log('Sequential Number Dashboard initialized');
    }

    // Initialize Chart.js charts
    initializeCharts() {
        // Sequence Timeline Chart
        const sequenceCtx = document.getElementById('sequenceChart').getContext('2d');
        this.charts.sequence = new Chart(sequenceCtx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: 'Sequence Numbers Generated',
                    data: [],
                    borderColor: '#3498db',
                    backgroundColor: 'rgba(52, 152, 219, 0.1)',
                    tension: 0.4,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        beginAtZero: true,
                        title: {
                            display: true,
                            text: 'Sequence Number'
                        }
                    },
                    x: {
                        title: {
                            display: true,
                            text: 'Time'
                        }
                    }
                },
                plugins: {
                    title: {
                        display: true,
                        text: 'Sequence Generation Timeline'
                    }
                }
            }
        });

        // Distribution Chart
        const distributionCtx = document.getElementById('distributionChart').getContext('2d');
        this.charts.distribution = new Chart(distributionCtx, {
            type: 'doughnut',
            data: {
                labels: [],
                datasets: [{
                    data: [],
                    backgroundColor: [
                        '#3498db',
                        '#e74c3c', 
                        '#f39c12',
                        '#27ae60',
                        '#9b59b6',
                        '#1abc9c'
                    ]
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: {
                        display: true,
                        text: 'Sequences by Site-Partition'
                    },
                    legend: {
                        position: 'bottom'
                    }
                }
            }
        });
    }

    // Load initial data
    async loadInitialData() {
        await this.updateStatistics();
        await this.updateHealth();
    }

    // Start auto-refresh
    startAutoRefresh() {
        if (this.isAutoRefreshing) return;
        
        this.isAutoRefreshing = true;
        
        // Update statistics every 3 seconds
        setInterval(() => {
            this.updateStatistics();
        }, 3000);
        
        // Update health every 5 seconds
        setInterval(() => {
            this.updateHealth();
        }, 5000);
    }

    // Generate sequence number
    async generateSequence(siteId, partitionId, invoiceType) {
        try {
            console.log(`Generating sequence for ${siteId}-${partitionId}-${invoiceType}`);
            
            const response = await axios.get('/api/v1/sequence/next', {
                params: {
                    siteId: siteId,
                    partitionId: partitionId,
                    invoiceType: invoiceType
                }
            });

            if (response.data.success) {
                const sequence = response.data.sequenceNumbers[0];
                console.log(`Generated sequence: ${sequence}`);
                
                // Update display
                this.updateLastSequence(response.data);
                
                // Add to history
                this.addToHistory({
                    sequence: sequence,
                    siteId: siteId,
                    partitionId: partitionId,
                    invoiceType: invoiceType,
                    isGapFilled: response.data.gapFilled,
                    timestamp: new Date(),
                    processingTime: response.data.processingTimeMs
                });

                // Update charts
                this.updateCharts();
                
                // Show success feedback
                this.showNotification(`Generated sequence ${sequence}`, 'success');
                
                return response.data;
            } else {
                throw new Error(response.data.error || 'Failed to generate sequence');
            }
            
        } catch (error) {
            console.error('Error generating sequence:', error);
            this.showNotification(`Error: ${error.message}`, 'error');
            throw error;
        }
    }

    // Update last sequence display
    updateLastSequence(response) {
        const sequenceElement = document.getElementById('lastSequence');
        const detailsElement = document.getElementById('sequenceDetails');
        
        const sequence = response.sequenceNumbers[0];
        sequenceElement.textContent = sequence;
        
        const gapText = response.gapFilled ? ' (Gap Filled!)' : '';
        const nodeText = response.nodeId ? ` • Node: ${response.nodeId}` : '';
        const timeText = response.processingTimeMs ? ` • ${response.processingTimeMs}ms` : '';
        
        detailsElement.innerHTML = `
            Site: ${response.siteId} • Partition: ${response.partitionId} • Type: ${response.invoiceType}
            ${gapText}${nodeText}${timeText}
        `;

        // Add animation effect
        sequenceElement.style.transform = 'scale(1.1)';
        setTimeout(() => {
            sequenceElement.style.transform = 'scale(1)';
        }, 200);
    }

    // Add to sequence history
    addToHistory(sequenceData) {
        this.sequenceHistory.unshift(sequenceData);
        
        // Keep only last 20 entries
        if (this.sequenceHistory.length > 20) {
            this.sequenceHistory = this.sequenceHistory.slice(0, 20);
        }
        
        this.updateHistoryDisplay();
    }

    // Update history display
    updateHistoryDisplay() {
        const historyElement = document.getElementById('sequenceHistory');
        
        if (this.sequenceHistory.length === 0) {
            historyElement.innerHTML = `
                <div style="text-align: center; color: #7f8c8d; padding: 2rem;">
                    <i class="fas fa-clock"></i>
                    <p>No sequences generated yet. Click the buttons above to start!</p>
                </div>
            `;
            return;
        }
        
        const historyHtml = this.sequenceHistory.map(item => {
            const gapBadge = item.isGapFilled ? '<span class="gap-badge">Gap Filled</span>' : '';
            const timeFormatted = item.timestamp.toLocaleTimeString();
            
            return `
                <div class="history-item">
                    <div>
                        <span class="sequence-number">#${item.sequence}</span>
                        ${gapBadge}
                    </div>
                    <div class="sequence-info">
                        ${item.siteId}-${item.partitionId} (${item.invoiceType})
                        <br>
                        <small>${timeFormatted} • ${item.processingTime || 0}ms</small>
                    </div>
                </div>
            `;
        }).join('');
        
        historyElement.innerHTML = historyHtml;
    }

    // Update statistics
    async updateStatistics() {
        try {
            document.getElementById('statsLoading').style.display = 'inline-flex';
            
            const response = await axios.get('/api/v1/sequence/stats');
            this.stats = response.data;
            
            // Update stat cards
            document.getElementById('currentCounter').textContent = this.stats.currentCounter || '-';
            document.getElementById('totalGenerated').textContent = this.stats.totalGenerated || '-';
            document.getElementById('availableGaps').textContent = this.stats.availableGaps || '-';
            document.getElementById('avgLatency').textContent = 
                this.stats.averageLatencyMs ? this.stats.averageLatencyMs.toFixed(2) : '-';
            
            // Update distribution chart
            this.updateDistributionChart();
            
        } catch (error) {
            console.error('Error updating statistics:', error);
        } finally {
            document.getElementById('statsLoading').style.display = 'none';
        }
    }

    // Update health status
    async updateHealth() {
        try {
            const response = await axios.get('/api/v1/sequence/health');
            const health = response.data;
            
            const healthElement = document.getElementById('systemHealth');
            
            if (health.healthy) {
                healthElement.className = 'health-indicator health-healthy';
                healthElement.innerHTML = `
                    <i class="fas fa-check-circle"></i>
                    <span>System Healthy • ${health.currentCounter} sequences generated</span>
                `;
            } else {
                healthElement.className = 'health-indicator health-down';
                healthElement.innerHTML = `
                    <i class="fas fa-exclamation-triangle"></i>
                    <span>System Issues Detected</span>
                `;
            }
            
        } catch (error) {
            console.error('Error checking health:', error);
            
            const healthElement = document.getElementById('systemHealth');
            healthElement.className = 'health-indicator health-down';
            healthElement.innerHTML = `
                <i class="fas fa-times-circle"></i>
                <span>Cannot Connect to System</span>
            `;
        }
    }

    // Update charts
    updateCharts() {
        // Update sequence timeline chart
        if (this.sequenceHistory.length > 0) {
            const labels = this.sequenceHistory.slice().reverse().map(item => 
                item.timestamp.toLocaleTimeString()
            );
            const data = this.sequenceHistory.slice().reverse().map(item => item.sequence);
            
            this.charts.sequence.data.labels = labels;
            this.charts.sequence.data.datasets[0].data = data;
            this.charts.sequence.update('none');
        }
    }

    // Update distribution chart
    updateDistributionChart() {
        if (this.stats.sequencesBySitePartition) {
            const labels = Object.keys(this.stats.sequencesBySitePartition);
            const data = Object.values(this.stats.sequencesBySitePartition);
            
            this.charts.distribution.data.labels = labels;
            this.charts.distribution.data.datasets[0].data = data;
            this.charts.distribution.update('none');
        }
    }

    // Run demonstration
    async runDemo(demoType) {
        console.log(`Running ${demoType} demo`);
        
        const resultElement = document.getElementById('demoResult');
        resultElement.style.display = 'block';
        resultElement.innerHTML = `
            <div class="demo-step">
                <i class="fas fa-spinner fa-spin"></i>
                Running ${demoType} demonstration...
            </div>
        `;
        
        try {
            let endpoint = `/api/v1/demo/${demoType}`;
            const response = await axios.post(endpoint);
            const result = response.data;
            
            // Display results
            let resultHtml = `<div class="demo-step demo-success">
                <strong>${result.name}</strong> - ${result.success ? 'SUCCESS' : 'FAILED'}
            </div>`;
            
            result.steps.forEach(step => {
                const isError = step.includes('ERROR') || step.includes('FAILED');
                const cssClass = isError ? 'demo-error' : 'demo-success';
                resultHtml += `<div class="demo-step ${cssClass}">${step}</div>`;
            });
            
            if (result.summary) {
                resultHtml += `<div class="demo-step" style="margin-top: 1rem; font-weight: bold;">
                    Summary: ${result.summary}
                </div>`;
            }
            
            resultElement.innerHTML = resultHtml;
            
            // Update statistics after demo
            setTimeout(() => {
                this.updateStatistics();
            }, 1000);
            
        } catch (error) {
            console.error(`Error running ${demoType} demo:`, error);
            resultElement.innerHTML = `
                <div class="demo-step demo-error">
                    <strong>Demo Failed:</strong> ${error.message}
                </div>
            `;
        }
    }

    // Show notification
    showNotification(message, type = 'info') {
        // Create notification element
        const notification = document.createElement('div');
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 1rem 1.5rem;
            border-radius: 8px;
            color: white;
            font-weight: 500;
            z-index: 1000;
            animation: slideIn 0.3s ease-out;
        `;
        
        // Set background color based on type
        const colors = {
            success: '#27ae60',
            error: '#e74c3c',
            warning: '#f39c12',
            info: '#3498db'
        };
        notification.style.backgroundColor = colors[type] || colors.info;
        
        // Set content
        notification.innerHTML = `
            <i class="fas fa-${type === 'success' ? 'check' : type === 'error' ? 'times' : 'info'}"></i>
            ${message}
        `;
        
        // Add CSS animation
        const style = document.createElement('style');
        style.textContent = `
            @keyframes slideIn {
                from { transform: translateX(100%); opacity: 0; }
                to { transform: translateX(0); opacity: 1; }
            }
            @keyframes slideOut {
                from { transform: translateX(0); opacity: 1; }
                to { transform: translateX(100%); opacity: 0; }
            }
        `;
        document.head.appendChild(style);
        
        // Add to page
        document.body.appendChild(notification);
        
        // Auto remove after 3 seconds
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease-in';
            setTimeout(() => {
                document.body.removeChild(notification);
                document.head.removeChild(style);
            }, 300);
        }, 3000);
    }

    // Release sequence (gap creation)
    async releaseSequence(sequenceNumber, siteId, partitionId, reason = 'manual-release') {
        try {
            console.log(`Releasing sequence ${sequenceNumber}`);
            
            const response = await axios.post('/api/v1/sequence/release', {
                sequenceNumber: sequenceNumber,
                siteId: siteId,
                partitionId: partitionId,
                reason: reason
            });

            if (response.data.success) {
                this.showNotification(`Released sequence ${sequenceNumber}`, 'warning');
                
                // Update statistics to show new gap
                setTimeout(() => {
                    this.updateStatistics();
                }, 500);
                
                return true;
            } else {
                throw new Error('Failed to release sequence');
            }
            
        } catch (error) {
            console.error('Error releasing sequence:', error);
            this.showNotification(`Error releasing sequence: ${error.message}`, 'error');
            return false;
        }
    }

    // Reset system
    async resetSystem() {
        if (!confirm('Are you sure you want to reset the entire system? This will clear all sequence data!')) {
            return;
        }
        
        try {
            console.log('Resetting system');
            
            const response = await axios.post('/api/v1/sequence/reset');
            
            if (response.data.success) {
                // Clear local data
                this.sequenceHistory = [];
                this.updateHistoryDisplay();
                
                // Reset display
                document.getElementById('lastSequence').textContent = '-';
                document.getElementById('sequenceDetails').textContent = 'System reset - ready for new sequences';
                
                // Update statistics
                this.updateStatistics();
                
                this.showNotification('System reset successfully', 'success');
                return true;
            } else {
                throw new Error(response.data.message || 'Reset failed');
            }
            
        } catch (error) {
            console.error('Error resetting system:', error);
            this.showNotification(`Reset failed: ${error.message}`, 'error');
            return false;
        }
    }
}

// Global functions for HTML onclick events
let dashboard;

function generateSequence(siteId, partitionId, invoiceType) {
    if (dashboard) {
        dashboard.generateSequence(siteId, partitionId, invoiceType);
    }
}

function runDemo(demoType) {
    if (dashboard) {
        dashboard.runDemo(demoType);
    }
}

function resetSystem() {
    if (dashboard) {
        dashboard.resetSystem();
    }
}

// Initialize dashboard when page loads
document.addEventListener('DOMContentLoaded', function() {
    dashboard = new SequenceDashboard();
    
    // Add reset button to demo section
    const demoButtons = document.querySelector('.demo-buttons');
    const resetButton = document.createElement('button');
    resetButton.className = 'btn btn-danger';
    resetButton.innerHTML = '<i class="fas fa-redo"></i> Reset System';
    resetButton.onclick = resetSystem;
    demoButtons.appendChild(resetButton);
});

// Handle page visibility changes to pause/resume updates
document.addEventListener('visibilitychange', function() {
    if (dashboard) {
        if (document.hidden) {
            console.log('Dashboard paused - page not visible');
        } else {
            console.log('Dashboard resumed - page visible');
            // Immediate update when returning to page
            dashboard.updateStatistics();
            dashboard.updateHealth();
        }
    }
});

// Handle errors globally
window.addEventListener('error', function(event) {
    console.error('Dashboard error:', event.error);
});

window.addEventListener('unhandledrejection', function(event) {
    console.error('Dashboard promise rejection:', event.reason);
});