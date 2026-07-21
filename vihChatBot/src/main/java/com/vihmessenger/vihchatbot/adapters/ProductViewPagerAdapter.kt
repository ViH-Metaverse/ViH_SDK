package com.vihmessenger.vihchatbot.adapters

import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.viewpager.widget.PagerAdapter
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.base.ThemeAwarePagerAdapter
import com.vihmessenger.vihchatbot.data.model.ProductModel
import com.vihmessenger.vihchatbot.databinding.ItemChatTemplateProductBinding
import com.vihmessenger.vihchatbot.ui.custom.CustomViewPager
import com.vihmessenger.vihchatbot.utils.extensions.dpToPx
import com.vihmessenger.vihchatbot.utils.extensions.withAlpha


class ProductViewPagerAdapter(var context: Context, var productList: List<ProductModel>) :
    ThemeAwarePagerAdapter() {

    override fun getCount(): Int {
        return productList.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object` as ConstraintLayout
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(context)
        // Create a new binding for each page
        val viewBinder = ItemChatTemplateProductBinding.inflate(inflater, container, false)

        // Set a fixed width for the root view
        val params = viewBinder.root.layoutParams
        params.width =
            context.resources.getDimensionPixelSize(R.dimen._270sdp) // Adjust this value as needed
        viewBinder.root.layoutParams = params

        val circularProgressDrawable = CircularProgressDrawable(context)
        circularProgressDrawable.strokeWidth = 5f
        circularProgressDrawable.centerRadius = 30f
        circularProgressDrawable.start()


        if (!productList[position].img_url.isNullOrEmpty()) {
            CustomImageLoader.loadImageView(
                imageView = viewBinder.ivTemplateProduct,
                url = productList[position].img_url,
                progressBar = viewBinder.progressBar,
                onError = {
                    viewBinder.ivTemplateProduct.setImageResource(R.drawable.placeholder)
                    container.post { container.requestLayout() }
                },
                onSuccess = {
                    // Add this callback to trigger layout updates after image loads
                    container.post {
                        container.requestLayout()
                        (container as? CustomViewPager)?.invalidate()
                    }
                }
            )
        } else {
            viewBinder.ivTemplateProduct.visibility = View.GONE
        }

        if (productList[position].addtocrt == "1") {
            viewBinder.cvAddCart.visibility = View.VISIBLE
            viewBinder.cvAddCart.setOnClickListener {
                val url = productList[position].addttocrt_url ?: ""
                if (url.isNotBlank() && url.startsWith("http")) {
                    val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(urlIntent)
                } else {
                    Toast.makeText(context, "Invalid or missing URL", Toast.LENGTH_SHORT).show()
                }
            }
            val colorWithAlpha = (0x10 shl 24) or (secondaryColor and 0x00FFFFFF)
            val backgroundDrawable = viewBinder.cvAddCart.background
            if (backgroundDrawable is GradientDrawable) {
                backgroundDrawable.setColor(colorWithAlpha)
            }
            viewBinder.appCompatTextView5.setTextColor(secondaryColor)
            val drawable = AppCompatResources.getDrawable(context, R.drawable.ic_cart)?.let {
                DrawableCompat.wrap(it).mutate()
            }
            drawable?.let {
                DrawableCompat.setTint(it, secondaryColor)
                viewBinder.appCompatTextView5.setCompoundDrawablesWithIntrinsicBounds(null, null, it, null)
            }
        } else {
            viewBinder.cvAddCart.visibility = View.GONE
        }

        if (productList[position].buynw == "1") {
            viewBinder.cvBuyNow.visibility = View.VISIBLE
            viewBinder.cvBuyNow.setOnClickListener {
                val url = productList[position].buynw_url ?: ""
                if (url.isNotBlank() && url.startsWith("http")) {
                    val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(urlIntent)
                } else {
                    Toast.makeText(context, "Invalid or missing URL", Toast.LENGTH_SHORT).show()
                }
            }
            val colorWithAlpha = (0x10 shl 24) or (secondaryColor and 0x00FFFFFF)

            val drawable = viewBinder.cvBuyNow.background as? GradientDrawable
            drawable?.apply {
                setColor(colorWithAlpha) // Set background color with alpha
                setStroke(1.dpToPx(), secondaryColor) // Set stroke color
            }

            viewBinder.appCompatTextView6.setTextColor(secondaryColor)

            val drawable1 = viewBinder.cvAddCart.background as? GradientDrawable
            drawable1?.apply {
                setColor(colorWithAlpha) // Set background color with alpha
                setStroke(1.dpToPx(), secondaryColor) // Set stroke color
            }

            viewBinder.appCompatTextView5.setTextColor(secondaryColor)

        } else {
            viewBinder.cvBuyNow.visibility = View.GONE
        }

        viewBinder.tvTemplateProductName.text = productList[position].prod_nm
        viewBinder.tvTemplateProductPrice.text = "₹${productList[position].prod_prc}"
        viewBinder.tvTemplateProductDescription.text = productList[position].prod_dsc

        // Additionally, add this to request a layout update after the image loads
        viewBinder.ivTemplateProduct.post {
            container.requestLayout()
        }


        container.addView(viewBinder.root)
        return viewBinder.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as ConstraintLayout)
    }

    override fun getPageWidth(position: Int): Float {
        return .93f
    }
}