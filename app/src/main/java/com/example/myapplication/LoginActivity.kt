package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

// Activity for handling user login, registration, and password reset functionality
class LoginActivity : AppCompatActivity() {

    // Firebase authentication instance
    private lateinit var auth: FirebaseAuth

    // UI components
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var resetPasswordTextView: TextView
    private lateinit var switchToRegisterButton: Button
    private lateinit var switchToLoginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase authentication
        auth = FirebaseAuth.getInstance()

        // Link UI components with their corresponding views
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        resetPasswordTextView = findViewById(R.id.resetPasswordEmailEditText)
        switchToRegisterButton = findViewById(R.id.switchToRegisterButton)
        switchToLoginButton = findViewById(R.id.switchToLoginButton)

        // Default to login mode on app start
        showLoginUI()

        // Switch to register mode when the "Switch to Register" button is clicked
        switchToRegisterButton.setOnClickListener { showRegisterUI() }

        // Switch back to login mode when the "Switch to Login" button is clicked
        switchToLoginButton.setOnClickListener { showLoginUI() }

        // Handle login functionality when the login button is clicked
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "No email or password entered", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(email, password)
            }
        }

        // Handle user registration when the register button is clicked
        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
            } else if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            } else {
                registerUser(email, password)
            }
        }

        // Handle password reset when the "Forgot Password?" text is clicked
        resetPasswordTextView.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email to reset your password", Toast.LENGTH_SHORT).show()
            } else {
                resetPassword(email)
            }
        }
    }

    // Show the UI components for login mode
    private fun showLoginUI() {
        loginButton.visibility = View.VISIBLE
        registerButton.visibility = View.GONE
        confirmPasswordEditText.visibility = View.GONE
        resetPasswordTextView.visibility = View.VISIBLE
        switchToRegisterButton.visibility = View.VISIBLE
        switchToLoginButton.visibility = View.GONE
    }

    // Show the UI components for register mode
    private fun showRegisterUI() {
        loginButton.visibility = View.GONE
        registerButton.visibility = View.VISIBLE
        confirmPasswordEditText.visibility = View.VISIBLE
        resetPasswordTextView.visibility = View.GONE
        switchToRegisterButton.visibility = View.GONE
        switchToLoginButton.visibility = View.VISIBLE
    }

    // Handle user login with email and password
    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                // Save the login time for session management
                val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                sharedPrefs.edit().putLong("lastLoginTime", System.currentTimeMillis()).apply()

                // Navigate to the main activity
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // Close LoginActivity
            } else {
                Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handle user registration with email and password
    private fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Send a password reset email to the user
    private fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Password reset email sent.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}








