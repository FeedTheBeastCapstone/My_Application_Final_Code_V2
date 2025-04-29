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

        // Switch to register mode
        switchToRegisterButton.setOnClickListener { showRegisterUI() }

        // Switch back to login mode
        switchToLoginButton.setOnClickListener { showLoginUI() }

        // Handle login logic
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "No email or password entered", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(email, password)
            }
        }

        // Handle registration logic
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

        // Handle password reset
        resetPasswordTextView.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email to reset your password", Toast.LENGTH_SHORT).show()
            } else {
                resetPassword(email)
            }
        }
    }

    // Show login UI elements
    private fun showLoginUI() {
        loginButton.visibility = View.VISIBLE
        registerButton.visibility = View.GONE
        confirmPasswordEditText.visibility = View.GONE
        resetPasswordTextView.visibility = View.VISIBLE
        switchToRegisterButton.visibility = View.VISIBLE
        switchToLoginButton.visibility = View.GONE
    }

    // Show register UI elements
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
                val user = auth.currentUser

                // Reload user to make sure we get the latest email verification status
                user?.reload()?.addOnCompleteListener {
                    if (user.isEmailVerified) {
                        // Email is verified — allow access
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                        // Record login time
                        MainActivity.updateLoginTime(this)

                        // Launch main activity
                        startActivity(Intent(this, MainActivity::class.java))
                        finish() // Close LoginActivity
                    } else {
                        // Email not verified — deny access
                        auth.signOut()
                        Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                // Login failed
                Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handle user registration with email verification
    private fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser

                // Send email verification
                user?.sendEmailVerification()?.addOnCompleteListener { verifyTask ->
                    if (verifyTask.isSuccessful) {
                        Toast.makeText(this, "Registration successful! Please check your email to verify your account.", Toast.LENGTH_LONG).show()
                        // Do NOT log them in until they verify
                        auth.signOut()
                    } else {
                        // Verification email failed
                        Toast.makeText(this, "Failed to send verification email: ${verifyTask.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Registration failed
                Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Send password reset email
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









