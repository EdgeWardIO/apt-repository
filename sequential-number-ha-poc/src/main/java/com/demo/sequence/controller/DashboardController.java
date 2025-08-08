package com.demo.sequence.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving the web dashboard
 * 
 * Provides access to the HTML dashboards for monitoring and demonstration.
 */
@Controller
public class DashboardController {

    /**
     * Serve the main dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/dashboard.html";
    }

    /**
     * Root path redirect to dashboard
     */
    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    /**
     * Serve the HA dashboard (for HA demonstrations)
     */
    @GetMapping("/ha-dashboard")
    public String haDashboard() {
        // For now, redirect to main dashboard
        // This can be expanded with HA-specific features
        return "redirect:/dashboard";
    }
}